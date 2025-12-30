package com.ligitabl.api.scheduling;

import com.ligitabl.api.usecases.sync.SyncMatchesUseCase;
import com.ligitabl.api.usecases.sync.TriggerRoundFinalizationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

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
public class MatchSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchSyncScheduler.class);

    private final TaskScheduler taskScheduler;
    private final SyncMatchesUseCase syncMatchesUseCase;
    private final TriggerRoundFinalizationUseCase triggerFinalizationUseCase;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    private ScheduledFuture<?> currentTask;
    private volatile boolean running = false;

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
                        // On error, retry after 5 minutes
                        scheduleNextSync(Duration.ofMinutes(5));
                        return null;
                    },
                    success -> {
                        log.info("Match sync completed: processed={}, updated={}, newlyFinished={}",
                                success.matchesProcessed(),
                                success.matchesUpdated(),
                                success.newlyFinishedMatches());

                        // Check if finalization should be triggered
                        if (success.allMatchesComplete()) {
                            log.info("All matches complete, triggering finalization check");
                            triggerFinalization();
                        }

                        // Schedule next sync
                        log.info("Next sync scheduled in: {} ({})",
                                formatDuration(success.nextSchedule().delay()),
                                success.nextSchedule().reason());

                        scheduleNextSync(success.nextSchedule().delay());
                        return null;
                    }
            );

        } catch (Exception e) {
            log.error("Unexpected error during match sync", e);
            // On unexpected error, retry after 10 minutes
            scheduleNextSync(Duration.ofMinutes(10));
        } finally {
            running = false;
        }
    }

    private void triggerFinalization() {
        try {
            var result = triggerFinalizationUseCase.execute(
                    new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(competitionCode)
            );

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
                    }
            );
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
            return String.format("%d hour%s %d minute%s",
                    hours, hours == 1 ? "" : "s",
                    minutes, minutes == 1 ? "" : "s");
        } else {
            return String.format("%d minute%s",
                    minutes, minutes == 1 ? "" : "s");
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
