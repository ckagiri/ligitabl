package com.ligitabl.api.scheduling.syncmatches;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Match Sync Scheduler
 *
 * Dynamically schedules match synchronization based on match status.
 * Runs immediately on application startup.
 *
 * Frequency Rules:
 * - Live matches: Every 3 minutes
 * - Kickoff <= 10 min: Every 3 minutes
 * - Kickoff <= 60 min: Every 10 minutes
 * - Kickoff < 6 hours: Every 1 hour
 * - Default: Every 6 hours
 * - All matches complete: Trigger finalization immediately
 */
@Component
@ConditionalOnProperty(name = "ligitabl.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MatchSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchSyncScheduler.class);

    private final TaskScheduler taskScheduler;
    private final SyncMatchesUseCase syncMatchesUseCase;
    private final TriggerRoundFinalizationUseCase triggerFinalizationUseCase;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    @Value("${football-data.sync.retry-on-failure-minutes:5}")
    private long retryOnFailureMinutes;

    @Value("${football-data.sync.max-consecutive-failures:3}")
    private int maxConsecutiveFailures;

    private ScheduledFuture<?> currentTask;
    private volatile boolean running = false;
    private int consecutiveFailures = 0;

    public MatchSyncScheduler(
            TaskScheduler taskScheduler,
            SyncMatchesUseCase syncMatchesUseCase,
            TriggerRoundFinalizationUseCase triggerFinalizationUseCase) {
        this.taskScheduler = taskScheduler;
        this.syncMatchesUseCase = syncMatchesUseCase;
        this.triggerFinalizationUseCase = triggerFinalizationUseCase;
    }

    /**
     * Run immediately on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("MatchSyncScheduler: Starting initial sync on application startup");
        scheduleNextSync(Duration.ZERO);
    }

    /**
     * Execute sync and schedule next run based on result
     */
    private void executeSync() {
        if (running) {
            log.warn("Sync already running, skipping");
            return;
        }

        running = true;

        try {
            log.info("Executing match sync");

            var result = syncMatchesUseCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

            result.fold(
                    error -> {
                        log.error("Match sync failed: {}", error);

                        consecutiveFailures++;
                        if (consecutiveFailures >= maxConsecutiveFailures) {
                            log.error(
                                    "Match sync has failed {} times consecutively (threshold: {}).",
                                    consecutiveFailures,
                                    maxConsecutiveFailures);
                        }

                        scheduleNextSync(Duration.ofMinutes(retryOnFailureMinutes));
                        return null;
                    },
                    success -> {
                        consecutiveFailures = 0;

                        log.info(
                                "Match sync completed: processed={}, updated={}, newlyFinished={}",
                                success.matchesProcessed(),
                                success.matchesUpdated(),
                                success.newlyFinishedMatches());

                        if (success.allMatchesComplete()) {
                            log.info("All matches complete, triggering finalization check");
                            triggerFinalization();
                        }

                        log.info(
                                "Next sync scheduled in: {} ({})",
                                formatDuration(success.nextSchedule().delay()),
                                success.nextSchedule().reason());

                        scheduleNextSync(success.nextSchedule().delay());
                        return null;
                    });

        } catch (Exception e) {
            log.error("Unexpected error during match sync", e);

            consecutiveFailures++;
            if (consecutiveFailures >= maxConsecutiveFailures) {
                log.error(
                        "Match sync has failed {} times consecutively (threshold: {}).",
                        consecutiveFailures,
                        maxConsecutiveFailures);
            }

            scheduleNextSync(Duration.ofMinutes(retryOnFailureMinutes));
        } finally {
            running = false;
        }
    }

    private void triggerFinalization() {
        try {
            var result = triggerFinalizationUseCase.execute(
                    new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(competitionCode));

            result.fold(
                    error -> {
                        log.warn("Finalization trigger failed or blocked: {}", error);
                        return null;
                    },
                    success -> {
                        if (success.finalized()) {
                            log.info("Round finalized successfully: {}", success.message());
                        } else if (success.blocked()) {
                            log.warn("Round finalization blocked: {}", success.message());
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error triggering finalization", e);
        }
    }

    private void scheduleNextSync(Duration delay) {
        // Cancel previous task if exists
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }

        // Schedule next execution
        Instant nextRun = Instant.now().plus(delay);
        currentTask = taskScheduler.schedule(this::executeSync, nextRun);

        log.debug("Next sync scheduled for: {}", nextRun);
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;

        if (hours > 0) {
            return String.format(
                    "%d hour%s %d minute%s", hours, hours == 1 ? "" : "s", minutes, minutes == 1 ? "" : "s");
        } else {
            return String.format("%d minute%s", minutes, minutes == 1 ? "" : "s");
        }
    }

    /**
     * For testing/manual trigger
     */
    public void triggerManualSync() {
        log.info("Manual sync triggered");
        executeSync();
    }
}
