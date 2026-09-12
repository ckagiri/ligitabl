package com.ligitabl.api.scheduling.seasonactivation;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.scheduling.health.JobHeartbeat;
import com.ligitabl.api.scheduling.health.JobNames;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Watches the outgoing (active) season and auto-promotes Competition.upcomingSeasonId to
 * activeSeasonId once the outgoing season is completed and its pre-season window has opened.
 *
 * Requires an upcoming season to already be assigned to the competition (via admin) —
 * does not derive one. Runs every 15 minutes. Idempotent — safe to call multiple times.
 */
@Service
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class SeasonActivationService {

    /** 15m cadence; 45m allows three missed ticks. */
    private static final Duration MAX_INTERVAL = Duration.ofMinutes(45);

    private final CompetitionDefaults competitionDefaults;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final AdminNotificationService adminNotificationService;
    private final Clock clock;
    private final JobHeartbeat heartbeat;

    /**
     * The heartbeat is the only evidence this job runs at all — it promotes a season roughly once
     * per season and is otherwise silent. A ping means the check completed, not that it promoted.
     */
    @PostConstruct
    void registerHeartbeat() {
        heartbeat.register(JobNames.SEASON_ACTIVATION, MAX_INTERVAL);
    }

    /**
     * Hand-written try/catch rather than {@code ScheduledJobRunner}: this method is
     * {@code @Transactional}, and a wrapped lambda would be a self-invocation that bypasses the
     * proxy and loses the transaction.
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @Transactional
    public void checkAndActivateUpcomingSeason() {
        try {
            var slug = competitionDefaults.defaultCompetitionSlug();
            competitionRepo
                    .findBySlug(slug)
                    .ifPresentOrElse(
                            this::maybeSwitch,
                            // Else a misconfigured slug reads as "ran fine" forever.
                            () -> log.warn(
                                    "[SEASON_ACTIVATION] Default competition '{}' not found; no activation check ran",
                                    slug));
            heartbeat.ping(JobNames.SEASON_ACTIVATION);
        } catch (Exception e) {
            // Sentry explicitly: additivity="false" keeps log.error out of it in this package.
            log.error("[SEASON_ACTIVATION_FAILED] {}", e.getMessage(), e);
            Sentry.captureException(e);
        }
    }

    private void maybeSwitch(Competition competition) {
        if (competition.getActiveSeasonId() == null) {
            return;
        }

        Season activeSeason =
                seasonRepo.findById(competition.getActiveSeasonId()).orElse(null);
        if (activeSeason == null) {
            return;
        }

        if (!activeSeason.isCompleted() || !activeSeason.isPreSeasonOpen(clock.instant())) {
            return;
        }

        if (competition.getUpcomingSeasonId() == null) {
            log.debug(
                    "[SEASON_ACTIVATION] No upcoming season assigned for competition {}, no switch",
                    competition.getSlug());
            return;
        }

        log.info(
                "[SEASON_ACTIVATION] Promoting competition {} upcomingSeasonId {} to activeSeasonId (was {})",
                competition.getSlug(),
                competition.getUpcomingSeasonId(),
                activeSeason.getId());
        competitionRepo.promoteUpcomingSeason(
                competition.getId(), competition.getUpcomingSeasonId(), activeSeason.getId());
        adminNotificationService.notifySeasonActivated(
                competition.getSlug().value(), competition.getUpcomingSeasonId(), activeSeason.getId());
    }
}
