package com.ligitabl.api.scheduling.seasonactivation;

import java.time.Clock;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

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

    private final CompetitionDefaults competitionDefaults;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final AdminNotificationService adminNotificationService;
    private final Clock clock;

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @Transactional
    public void checkAndActivateUpcomingSeason() {
        competitionRepo.findBySlug(competitionDefaults.defaultCompetitionSlug()).ifPresent(this::maybeSwitch);
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
