package com.ligitabl.api.scheduling.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.outbox.SeasonInPlayEnqueuer;

import io.sentry.Sentry;
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
// Both flags, matching OutboxRelayJob: ligitabl.scheduling.enabled is the global scheduler
// kill-switch and must be able to stop this too — auto-join writes real rows, so a job that
// ignored it would keep mutating data in environments (tests, maintenance) that had explicitly
// turned scheduling off.
@ConditionalOnProperty(
        name = {"ligitabl.scheduling.enabled", "ligitabl.auto-join.enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class SeasonInPlayJob {

    private final SeasonInPlayEnqueuer seasonInPlayEnqueuer;

    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60 * 1000)
    public void run() {
        try {
            seasonInPlayEnqueuer.enqueueIfSeasonInPlay();
        } catch (Exception e) {
            log.error("Season in-play auto-join job failed", e);
            Sentry.captureException(e);
        }
    }
}
