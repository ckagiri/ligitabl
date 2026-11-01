package com.ligitabl.api.scheduling.seasonactivation;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.scheduling.health.JobHeartbeat;
import com.ligitabl.api.scheduling.health.JobNames;
import com.ligitabl.api.scheduling.health.ScheduledJobRunner;
import com.ligitabl.api.usecase.season.switchseason.AutoActivateSeasonResult;
import com.ligitabl.api.usecase.season.switchseason.AutoActivateSeasonUseCase;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Polls whether the default competition's upcoming season is due to go active. */
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class SeasonActivationJob {

    /** 15m cadence; 45m allows three missed ticks. */
    private static final Duration MAX_INTERVAL = Duration.ofMinutes(45);

    private final CompetitionDefaults competitionDefaults;
    private final AutoActivateSeasonUseCase autoActivateSeasonUseCase;
    private final ScheduledJobRunner runner;
    private final JobHeartbeat heartbeat;

    /**
     * The heartbeat is the only evidence this job runs at all — it promotes a season roughly once
     * per season and is otherwise silent. A ping means the check completed, not that it promoted.
     */
    @PostConstruct
    void registerHeartbeat() {
        heartbeat.register(JobNames.SEASON_ACTIVATION, MAX_INTERVAL);
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void run() {
        runner.runSafely(JobNames.SEASON_ACTIVATION, this::checkDefaultCompetition);
    }

    private void checkDefaultCompetition() {
        var slug = competitionDefaults.defaultCompetitionSlug();
        if (autoActivateSeasonUseCase.execute(slug) instanceof AutoActivateSeasonResult.CompetitionNotFound) {
            // Else a misconfigured slug reads as "ran fine" forever.
            log.warn("[SEASON_ACTIVATION] Default competition '{}' not found; no activation check ran", slug);
        }
    }
}
