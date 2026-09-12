package com.ligitabl.api.scheduling.outbox;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.outbox.SeasonInPlayEnqueuer;
import com.ligitabl.api.scheduling.health.JobHeartbeat;
import com.ligitabl.api.scheduling.health.JobNames;
import com.ligitabl.api.scheduling.health.ScheduledJobRunner;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls for the pre-season → in-play transition. {@code fixedDelay} rather than a cron because
 * the window this must catch is short (round 1 open) and a restart should resume checking
 * promptly — which also removes any need for a separate startup-recovery bean.
 */
@Component
@RequiredArgsConstructor
@Slf4j
// ligitabl.scheduling.enabled is the global scheduler kill-switch, auto-join writes real rows,
// so a job that ignored it would keep mutating data in environments (tests, staging) that had
// explicitly turned scheduling off.
@ConditionalOnProperty(
        name = {"ligitabl.scheduling.enabled", "ligitabl.auto-join.enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class SeasonInPlayJob {

    /** 15m cadence; 45m allows three missed ticks. */
    private static final Duration HEARTBEAT_MAX_INTERVAL = Duration.ofMinutes(45);

    private final SeasonInPlayEnqueuer seasonInPlayEnqueuer;
    private final ScheduledJobRunner runner;
    private final JobHeartbeat heartbeat;

    @PostConstruct
    void registerHeartbeat() {
        heartbeat.register(JobNames.SEASON_IN_PLAY, HEARTBEAT_MAX_INTERVAL);
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60 * 1000)
    public void run() {
        runner.runSafely(JobNames.SEASON_IN_PLAY, seasonInPlayEnqueuer::enqueueIfSeasonInPlay);
    }
}
