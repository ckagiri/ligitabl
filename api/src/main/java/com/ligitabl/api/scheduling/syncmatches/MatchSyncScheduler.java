package com.ligitabl.api.scheduling.syncmatches;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundLockedPayload;
import com.ligitabl.api.scheduling.advanceround.RoundAdvancementService;
import com.ligitabl.api.scheduling.health.JobHeartbeat;
import com.ligitabl.api.scheduling.health.JobNames;
import com.ligitabl.api.scheduling.resilience.MatchSyncCircuitBreaker;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.OutboxRepo;
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
 * Admin Slack notifications are posted whenever {@link NextSyncSchedule#shouldNotify()} is true —
 * every schedule above except the high-frequency live/imminent/soon polling branches, which are
 * deliberately silent on repeats (they'd otherwise post on every minute-by-minute reason change)
 * but still notify once on entry into a new {@link NextSyncSchedule.Phase}.
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
    private final OutboxRepo outboxRepo;
    private final ObjectMapper objectMapper;
    private final MatchSyncCircuitBreaker circuitBreaker;
    private final JobHeartbeat heartbeat;

    private static final Duration SETUP_MODE_DEFER_DELAY = Duration.ofMinutes(30);

    /** The legitimate worst case is the 24h season-complete cadence, not the usual 6h. */
    private static final Duration HEARTBEAT_MAX_INTERVAL = Duration.ofHours(30);

    /**
     * Floor on every scheduled delay. A zero delay fires the next run while the current one is
     * still inside result.fold(), which orphaned the chain on 2026-09-06 (.art/task_89.md).
     */
    private static final Duration MIN_SYNC_DELAY = Duration.ofSeconds(30);

    /** Above the floor deliberately: retrying at the floor tends to hit the same contention. */
    private static final Duration REENTRANT_SKIP_RETRY = Duration.ofSeconds(60);

    @Value("${football-data.competition.code}")
    private String competitionCode;

    @Value("${football-data.sync.retry-on-failure-minutes:5}")
    private long retryOnFailureMinutes;

    private ScheduledFuture<?> currentTask;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Tracks the last observed LIVE/IMMINENT/SOON phase so we can notify once on entry into a
    // new phase without spamming on every repeat within it (those schedules never set shouldNotify).
    private volatile NextSyncSchedule.Phase lastPhase = NextSyncSchedule.Phase.NONE;

    public MatchSyncScheduler(
            TaskScheduler taskScheduler,
            SyncMatchesUseCase syncMatchesUseCase,
            TriggerRoundFinalizationUseCase triggerFinalizationUseCase,
            AdminNotificationService adminNotificationService,
            RoundAdvancementService roundAdvancementService,
            SeasonRepo seasonRepo,
            OutboxRepo outboxRepo,
            ObjectMapper objectMapper,
            MatchSyncCircuitBreaker circuitBreaker,
            JobHeartbeat heartbeat) {
        this.taskScheduler = taskScheduler;
        this.syncMatchesUseCase = syncMatchesUseCase;
        this.triggerFinalizationUseCase = triggerFinalizationUseCase;
        this.adminNotificationService = adminNotificationService;
        this.roundAdvancementService = roundAdvancementService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.seasonRepo = seasonRepo;
        this.circuitBreaker = circuitBreaker;
        this.heartbeat = heartbeat;
    }

    /**
     * Starts the sync chain on startup. The requested zero delay is floored to
     * {@link #MIN_SYNC_DELAY} like every other path — deliberate, so no caller can schedule at
     * "now" while the app is still warming.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("MatchSyncScheduler: Scheduling initial sync on application startup");
        heartbeat.register(JobNames.MATCH_SYNC, HEARTBEAT_MAX_INTERVAL);
        adminNotificationService.notifyStartup(competitionCode);
        scheduleNextSync(Duration.ZERO);
    }

    /**
     * Execute sync and schedule next run based on result
     */
    private void executeSync() {
        if (!running.compareAndSet(false, true)) {
            // Must re-arm: returning empty-handed here is what orphaned the chain on 2026-09-06.
            log.warn("Sync already running, rescheduling in {}", formatDuration(REENTRANT_SKIP_RETRY));
            scheduleNextSync(REENTRANT_SKIP_RETRY);
            return;
        }

        try {
            if (!circuitBreaker.allowRequest()) {
                // Inside the guard so `finally` clears `running` — this path reschedules too, so
                // leaving it outside gave it the same orphaning shape as the bug above.
                var delay = circuitBreaker.getRemainingRecoveryTime().plusMinutes(1);
                log.warn("Circuit breaker open - deferring sync by {}", formatDuration(delay));
                // The chain is alive and the breaker alerts separately, so this counts as healthy
                // for the watchdog's purposes.
                heartbeat.ping(JobNames.MATCH_SYNC);
                scheduleNextSync(delay);
                return;
            }

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
                        heartbeat.ping(JobNames.MATCH_SYNC);

                        log.info(
                                "Match sync completed: processed={}, updated={}, newlyFinished={}",
                                success.matchesProcessed(),
                                success.matchesUpdated(),
                                success.newlyFinishedMatches());

                        maybeWriteRoundLockedEvent(success);

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

                        var phase = success.nextSchedule().phase();
                        boolean enteringPhase = phase != NextSyncSchedule.Phase.NONE && phase != lastPhase;
                        lastPhase = phase;

                        if (success.nextSchedule().shouldNotify() || enteringPhase) {
                            adminNotificationService.notifySyncScheduleChanged(
                                    success.roundId(),
                                    success.roundPosition(),
                                    success.nextSchedule().delay(),
                                    success.nextSchedule().reason());
                        }

                        scheduleNextSync(success.nextSchedule().delay());
                        return null;
                    });

        } catch (Exception e) {
            log.error("Unexpected error during match sync", e);
            Sentry.captureException(e);

            circuitBreaker.recordFailure();

            scheduleNextSync(Duration.ofMinutes(retryOnFailureMinutes));
        } finally {
            running.set(false);
        }
    }

    /**
     * Writes a ROUND_LOCKED outbox event whenever the round currently reads as
     * LOCKED. The idempotency key ("round-locked:{roundId}") means only the
     * first write per round actually inserts (ON CONFLICT DO NOTHING) — every
     * later sync tick while the round stays LOCKED is a harmless no-op.
     */
    private void maybeWriteRoundLockedEvent(MatchSyncResult result) {
        if (result.roundId() == null || result.roundStatus() != RoundStatus.LOCKED) {
            return;
        }
        try {
            var payload = new RoundLockedPayload(result.seasonId(), result.roundId(), result.roundPosition());
            var event = OutboxEvent.create(
                    "round-locked:" + result.roundId(),
                    OutboxEventTypes.ROUND_LOCKED,
                    "round",
                    result.roundId().toString(),
                    objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception e) {
            log.error("Failed to write ROUND_LOCKED outbox event for round {}", result.roundId(), e);
            Sentry.captureException(e);
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

    /**
     * The one choke point every caller passes through, so the floor is applied here rather than in
     * {@link SyncFrequencyCalculator} — {@code immediate()} stays meaningful, and {@code onStartup}
     * bypasses the calculator entirely.
     *
     * <p>{@code synchronized} also stops two pool threads cancelling each other's freshly-scheduled
     * task; the method does no blocking work.
     */
    private synchronized void scheduleNextSync(Duration requested) {
        Duration delay = (requested == null || requested.compareTo(MIN_SYNC_DELAY) < 0) ? MIN_SYNC_DELAY : requested;

        // Mostly a no-op when called from inside executeSync: currentTask is then the running
        // one-shot, already past cancellation.
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }

        // ⚠️ A deliberate wall-clock read — do not route this through the application `Clock` bean.
        // This instant is handed straight to `taskScheduler`, which fires on real time.
        Instant nextRun = Instant.now().plus(delay);
        currentTask = taskScheduler.schedule(this::executeSync, Objects.requireNonNull(nextRun));

        log.info("Next sync scheduled for: {} (in {})", nextRun, formatDuration(delay));
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.toSeconds() % 60;

        if (hours > 0) {
            return String.format(
                    "%d hour%s %d minute%s", hours, hours == 1 ? "" : "s", minutes, minutes == 1 ? "" : "s");
        } else if (minutes > 0) {
            return String.format("%d minute%s", minutes, minutes == 1 ? "" : "s");
        } else {
            // Without this the 30s floor and 60s skip-retry both log as "0 minutes".
            return String.format("%d second%s", seconds, seconds == 1 ? "" : "s");
        }
    }

    /**
     * For testing/manual trigger. During an in-flight sync this now schedules a retry rather than
     * silently doing nothing.
     */
    public void triggerManualSync() {
        log.info("Manual sync triggered");
        executeSync();
    }
}
