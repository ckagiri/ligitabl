package com.ligitabl.api.scheduling.syncmatches;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.rest.round.finalizeround.AdvanceCurrentRoundUseCase;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

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
 */
@Component
@ConditionalOnProperty(name = "ligitabl.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MatchSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchSyncScheduler.class);

    private final TaskScheduler taskScheduler;
    private final SyncMatchesUseCase syncMatchesUseCase;
    private final TriggerRoundFinalizationUseCase triggerFinalizationUseCase;
    private final AdminNotificationService adminNotificationService;
    private final AdvanceCurrentRoundUseCase advanceCurrentRoundUseCase;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    @Value("${football-data.sync.retry-on-failure-minutes:5}")
    private long retryOnFailureMinutes;

    @Value("${football-data.sync.max-consecutive-failures:3}")
    private int maxConsecutiveFailures;

    @Value("${ligitabl.round-advancement.delay-minutes:3}")
    private long advancementDelayMinutes;

    private ScheduledFuture<?> currentTask;
    private volatile boolean running = false;
    private int consecutiveFailures = 0;

    public MatchSyncScheduler(
            TaskScheduler taskScheduler,
            SyncMatchesUseCase syncMatchesUseCase,
            TriggerRoundFinalizationUseCase triggerFinalizationUseCase,
            AdminNotificationService adminNotificationService,
            AdvanceCurrentRoundUseCase advanceCurrentRoundUseCase,
            SeasonRepo seasonRepo,
            RoundRepo roundRepo) {
        this.taskScheduler = taskScheduler;
        this.syncMatchesUseCase = syncMatchesUseCase;
        this.triggerFinalizationUseCase = triggerFinalizationUseCase;
        this.adminNotificationService = adminNotificationService;
        this.advanceCurrentRoundUseCase = advanceCurrentRoundUseCase;
        this.seasonRepo = seasonRepo;
        this.roundRepo = roundRepo;
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
                            log.info(
                                    "All matches complete; triggering finalization check",
                                    success.allMatchesComplete());
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
                            scheduleRoundAdvancement(syncResult.seasonId(), syncResult.roundId());
                        } else if (success.blocked()) {
                            log.warn("Round finalization blocked: {}", success.message());
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error triggering finalization", e);
        }
    }

    private void scheduleRoundAdvancement(UUID seasonId, UUID roundId) {
        if (seasonId == null) {
            return;
        }

        if (advancementDelayMinutes <= 0) {
            log.info("Round advancement delay is 0, advancing immediately for season={}, round={}", seasonId, roundId);
            executeDelayedAdvancement(seasonId, roundId);
            return;
        }

        log.info("Scheduling round advancement in {} minutes for season={}, round={}",
                advancementDelayMinutes, seasonId, roundId);

        Instant runAt = Instant.now().plus(Duration.ofMinutes(advancementDelayMinutes));
        taskScheduler.schedule(() -> executeDelayedAdvancement(seasonId, roundId), runAt);
    }

    private void executeDelayedAdvancement(UUID seasonId, UUID roundId) {
        try {
            log.info("Executing delayed round advancement for season={}, round={}", seasonId, roundId);

            // Guard: verify state hasn't changed (e.g. admin manually advanced)
            var season = seasonRepo.findById(seasonId).orElse(null);
            if (season == null || !roundId.equals(season.getCurrentRoundId())) {
                log.warn("Skipping delayed advancement: state has changed (season={}, expectedRound={})",
                        seasonId, roundId);
                return;
            }

            var round = roundRepo.findById(roundId).orElse(null);
            if (round == null || !round.isFinalized()) {
                log.warn("Skipping delayed advancement: round not finalized (season={}, round={})",
                        seasonId, roundId);
                return;
            }

            var advanceResult = advanceCurrentRoundUseCase.execute(
                    new AdvanceCurrentRoundUseCase.AdvanceCurrentRoundCommand(seasonId, roundId));

            advanceResult.fold(
                    error -> {
                        log.warn("AdvanceCurrentRound failed: {}", error);
                        return null;
                    },
                    advance -> {
                        if (advance.seasonCompleted()) {
                            log.info("Season completed after finalization (seasonId={})", seasonId);
                        } else if (advance.advanced()) {
                            log.info("Advanced to next round: {} (seasonId={})",
                                    advance.newRoundPosition(), seasonId);
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error during delayed round advancement for season={}, round={}", seasonId, roundId, e);
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
