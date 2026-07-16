package com.ligitabl.api.scheduling.syncmatches;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.scheduling.advanceround.RoundAdvancementService;
import com.ligitabl.api.scheduling.resilience.MatchSyncCircuitBreaker;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.SeasonRepo;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;

/**
 * Match Sync Scheduler
 *
 * Dynamically schedules match synchronization based on match status.
 * Runs immediately on application startup.
 *
 * Frequency rules are determined by {@link SyncFrequencyCalculator}:
 * - All matches complete: Immediate (trigger finalization)
 * - Round obstructed: Immediate (trigger admin notification), then 2h backoff
 * - Season complete: Every 24 hours
 * - No upcoming matches: Every 12 hours
 * - Live matches: Every 90 seconds
 * - Kickoff <= 10 min: Every 1 minute
 * - Kickoff <= 60 min: Every 10 minutes
 * - Kickoff < 6 hours: Every 1 hour
 * - Default: Every 6 hours
 * - Suspended match present: at most every 10 minutes
 * - Cancelled match present: at most every 30 minutes
 *
 * Repeated sync failures trip {@link com.ligitabl.api.scheduling.resilience.MatchSyncCircuitBreaker},
 * which blocks further attempts until its recovery period elapses.
 */
@Component
@ConditionalOnProperty(name = "ligitabl.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class MatchSyncScheduler {

    private final TaskScheduler taskScheduler;
    private final SyncMatchesUseCase syncMatchesUseCase;
    private final TriggerRoundFinalizationUseCase triggerFinalizationUseCase;
    private final AdminNotificationService adminNotificationService;
    private final RoundAdvancementService roundAdvancementService;
    private final SeasonRepo seasonRepo;
    private final MatchSyncCircuitBreaker circuitBreaker;

    private static final Duration SETUP_MODE_DEFER_DELAY = Duration.ofMinutes(30);

    @Value("${football-data.competition.code}")
    private String competitionCode;

    @Value("${football-data.sync.retry-on-failure-minutes:5}")
    private long retryOnFailureMinutes;

    private ScheduledFuture<?> currentTask;
    private volatile boolean running = false;

    public MatchSyncScheduler(
            TaskScheduler taskScheduler,
            SyncMatchesUseCase syncMatchesUseCase,
            TriggerRoundFinalizationUseCase triggerFinalizationUseCase,
            AdminNotificationService adminNotificationService,
            RoundAdvancementService roundAdvancementService,
            SeasonRepo seasonRepo,
            MatchSyncCircuitBreaker circuitBreaker) {
        this.taskScheduler = taskScheduler;
        this.syncMatchesUseCase = syncMatchesUseCase;
        this.triggerFinalizationUseCase = triggerFinalizationUseCase;
        this.adminNotificationService = adminNotificationService;
        this.roundAdvancementService = roundAdvancementService;
        this.seasonRepo = seasonRepo;
        this.circuitBreaker = circuitBreaker;
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

        if (!circuitBreaker.allowRequest()) {
            // Re-check shortly after the recovery window opens
            var delay = circuitBreaker.getRemainingRecoveryTime().plusMinutes(1);
            log.warn("Circuit breaker open - deferring sync by {}", formatDuration(delay));
            scheduleNextSync(delay);
            return;
        }

        running = true;

        try {
            log.info("Executing match sync");

            var result = syncMatchesUseCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

            result.fold(
                    error -> {
                        log.error("Match sync failed: {}", error);

                        circuitBreaker.recordFailure();

                        scheduleNextSync(Duration.ofMinutes(retryOnFailureMinutes));
                        return null;
                    },
                    success -> {
                        circuitBreaker.recordSuccess();

                        log.info(
                                "Match sync completed: processed={}, updated={}, newlyFinished={}",
                                success.matchesProcessed(),
                                success.matchesUpdated(),
                                success.newlyFinishedMatches());

                        if (success.roundObstructed()) {
                            log.warn(
                                    "Round obstructed (roundId={}, position={}, obstructedMatches={}); notifying admin",
                                    success.roundId(),
                                    success.roundPosition(),
                                    success.obstructedMatchIds().size());

                            var matchIds = success.obstructedMatchIds();
                            var details = matchIds.stream()
                                    .map(id -> "- Match ID: " + id)
                                    .toList();

                            adminNotificationService.notifyBlockedFinalization(
                                    success.roundId(), success.roundPosition(), matchIds, details);

                            // Avoid tight loops when obstructed: back off even though NextSyncSchedule may be
                            // immediate.
                            scheduleNextSync(Duration.ofHours(2));
                            return null;
                        }

                        if (success.allMatchesComplete()) {
                            if (isSeasonInSetupMode(success.seasonId())) {
                                log.info(
                                        "All matches complete but season is in setup mode; deferring "
                                                + "finalization check by {}",
                                        formatDuration(SETUP_MODE_DEFER_DELAY));
                                scheduleNextSync(SETUP_MODE_DEFER_DELAY);
                                return null;
                            }
                            log.info("All matches complete; triggering finalization check");
                            triggerFinalization(success);
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
            Sentry.captureException(e);

            circuitBreaker.recordFailure();

            scheduleNextSync(Duration.ofMinutes(retryOnFailureMinutes));
        } finally {
            running = false;
        }
    }

    private boolean isSeasonInSetupMode(UUID seasonId) {
        if (seasonId == null) {
            return false;
        }
        return seasonRepo.findById(seasonId).map(Season::isInSetupMode).orElse(false);
    }

    private void triggerFinalization(MatchSyncResult syncResult) {
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
                            scheduleRoundAdvancement(syncResult.roundId(), syncResult.seasonId());
                        } else if (success.blocked()) {
                            log.warn("Round finalization blocked: {}", success.message());
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error triggering finalization", e);
            Sentry.captureException(e);
        }
    }

    private void scheduleRoundAdvancement(UUID roundId, UUID seasonId) {
        if (seasonId == null || roundId == null) {
            return;
        }
        try {
            roundAdvancementService.scheduleAdvancement(roundId, seasonId);
        } catch (Exception e) {
            log.error("Failed to schedule round advancement: round={}, season={}", roundId, seasonId, e);
            Sentry.captureException(e);
        }
    }

    private void scheduleNextSync(Duration delay) {
        // Cancel previous task if exists
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }

        // Schedule next execution
        Instant nextRun = Instant.now().plus(delay);
        currentTask = taskScheduler.schedule(this::executeSync, Objects.requireNonNull(nextRun));

        log.info("Next sync scheduled for: {}", nextRun);
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
