package com.ligitabl.api.scheduling.outbox;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.api.scheduling.health.JobHeartbeat;
import com.ligitabl.api.scheduling.health.JobNames;
import com.ligitabl.api.scheduling.health.ScheduledJobRunner;
import com.ligitabl.api.usecase.notification.seasoninplay.EnqueueSeasonInPlayUseCase;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls for the pre-season → in-play transition. {@code fixedDelay} rather than a cron: the window
 * (round 1 open) is short, and a restart resumes checking without a startup-recovery bean.
 */
@Component
@RequiredArgsConstructor
@Slf4j
// Honours the global scheduling switch: auto-join writes real rows, and tests and staging turn
// scheduling off.
@ConditionalOnProperty(
        name = {"ligitabl.scheduling.enabled", "ligitabl.auto-join.enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class SeasonInPlayJob {

    /** 15m cadence; 45m allows three missed ticks. */
    private static final Duration HEARTBEAT_MAX_INTERVAL = Duration.ofMinutes(45);

    private final EnqueueSeasonInPlayUseCase enqueueSeasonInPlayUseCase;
    private final ScheduledJobRunner runner;
    private final JobHeartbeat heartbeat;

    @PostConstruct
    void registerHeartbeat() {
        heartbeat.register(JobNames.SEASON_IN_PLAY, HEARTBEAT_MAX_INTERVAL);
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60 * 1000)
    public void run() {
        runner.runSafely(JobNames.SEASON_IN_PLAY, enqueueSeasonInPlayUseCase::execute);
    }
}
