package com.ligitabl.api.scheduling.syncmatches;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.TaskScheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.scheduling.advanceround.RoundAdvancementService;
import com.ligitabl.api.scheduling.resilience.MatchSyncCircuitBreaker;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchSyncSchedulerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SyncMatchesUseCase syncMatchesUseCase;

    @Mock
    private TriggerRoundFinalizationUseCase triggerFinalizationUseCase;

    @Mock
    private AdminNotificationService adminNotificationService;

    @Mock
    private RoundAdvancementService roundAdvancementService;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private OutboxRepo outboxRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MatchSyncCircuitBreaker circuitBreaker;

    @Mock
    private ScheduledFuture<Object> scheduledFuture;

    private MatchSyncScheduler scheduler;

    private UUID seasonId;
    private UUID roundId;

    @BeforeEach
    void setUp() throws Exception {
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        scheduler = new MatchSyncScheduler(
                taskScheduler,
                syncMatchesUseCase,
                triggerFinalizationUseCase,
                adminNotificationService,
                roundAdvancementService,
                seasonRepo,
                outboxRepo,
                objectMapper,
                circuitBreaker);

        setField(scheduler, "competitionCode", "PL");
        setField(scheduler, "retryOnFailureMinutes", 5L);

        when(circuitBreaker.allowRequest()).thenReturn(true);

        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void deferFinalization_whenAllMatchesCompleteAndSeasonInSetupMode() {
        Season season = Season.builder()
                .id(seasonId)
                .mainContestId(null) // in setup mode
                .detachedContestId(UUID.randomUUID())
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));

        MatchSyncResult result = completeResult();
        when(syncMatchesUseCase.execute(any())).thenReturn(Either.right(result));

        scheduler.triggerManualSync();

        verify(triggerFinalizationUseCase, never()).execute(any());

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        Duration delay = Duration.between(Instant.now(), instantCaptor.getValue());
        assertTrue(delay.toMinutes() >= 29 && delay.toMinutes() <= 30, "expected ~30 minute defer, got " + delay);
    }

    @Test
    void triggersFinalization_whenAllMatchesCompleteAndSeasonNotInSetupMode() {
        Season season = Season.builder()
                .id(seasonId)
                .mainContestId(UUID.randomUUID()) // not in setup mode
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));

        MatchSyncResult result = completeResult();
        when(syncMatchesUseCase.execute(any())).thenReturn(Either.right(result));
        when(triggerFinalizationUseCase.execute(any()))
                .thenReturn(Either.left(
                        new TriggerRoundFinalizationUseCase.TriggerFinalizationError.RoundNotFound(roundId)));

        scheduler.triggerManualSync();

        verify(triggerFinalizationUseCase).execute(any());
    }

    @Test
    void skipsSyncAndReschedules_whenCircuitBreakerOpen() {
        when(circuitBreaker.allowRequest()).thenReturn(false);
        when(circuitBreaker.getRemainingRecoveryTime()).thenReturn(Duration.ofMinutes(30));

        scheduler.triggerManualSync();

        verify(syncMatchesUseCase, never()).execute(any());

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        Duration delay = Duration.between(Instant.now(), instantCaptor.getValue());
        assertTrue(delay.toMinutes() >= 30 && delay.toMinutes() <= 31, "expected ~31 minute defer, got " + delay);
    }

    @Test
    void recordsFailure_whenSyncFails() {
        when(syncMatchesUseCase.execute(any()))
                .thenReturn(Either.left(new SyncMatchesUseCase.SyncMatchesError.HierarchyError(
                        UseCaseErrors.validation("Competition has no active season"))));

        scheduler.triggerManualSync();

        verify(circuitBreaker).recordFailure();
        verify(circuitBreaker, never()).recordSuccess();
    }

    @Test
    void recordsSuccess_whenSyncSucceeds() {
        Season season = Season.builder()
                .id(seasonId)
                .mainContestId(UUID.randomUUID()) // not in setup mode
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(syncMatchesUseCase.execute(any())).thenReturn(Either.right(completeResult()));
        when(triggerFinalizationUseCase.execute(any()))
                .thenReturn(Either.left(
                        new TriggerRoundFinalizationUseCase.TriggerFinalizationError.RoundNotFound(roundId)));

        scheduler.triggerManualSync();

        verify(circuitBreaker).recordSuccess();
        verify(circuitBreaker, never()).recordFailure();
    }

    @Test
    void onStartup_notifiesStartupAndSchedulesInitialSync() {
        scheduler.onStartup();

        verify(adminNotificationService).notifyStartup("PL");
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void notifiesEveryTime_whenScheduleIsFlaggedForNotification() {
        // Repeated identical reason (e.g. "No upcoming matches" polled every 12h) must still
        // notify each time — the old reason-text dedup silently swallowed these after the first.
        when(syncMatchesUseCase.execute(any()))
                .thenReturn(Either.right(resultWithReason("No upcoming matches - checking twice daily", true)))
                .thenReturn(Either.right(resultWithReason("No upcoming matches - checking twice daily", true)));

        scheduler.triggerManualSync();
        scheduler.triggerManualSync();

        verify(adminNotificationService, times(2))
                .notifySyncScheduleChanged(any(), anyInt(), any(), eq("No upcoming matches - checking twice daily"));
    }

    @Test
    void neverNotifies_forRepeatsWithinTheSamePhase() {
        // Live polling reasons are static text every 90s; these are deliberately silent on repeat.
        when(syncMatchesUseCase.execute(any()))
                .thenReturn(Either.right(resultWithPhase("Live matches in progress", NextSyncSchedule.Phase.LIVE)))
                .thenReturn(Either.right(resultWithPhase("Live matches in progress", NextSyncSchedule.Phase.LIVE)))
                .thenReturn(Either.right(resultWithPhase("Live matches in progress", NextSyncSchedule.Phase.LIVE)));

        scheduler.triggerManualSync();
        scheduler.triggerManualSync();
        scheduler.triggerManualSync();

        // Only the entry into LIVE (first call) notifies; the two repeats stay silent.
        verify(adminNotificationService, times(1)).notifySyncScheduleChanged(any(), anyInt(), any(), any());
    }

    @Test
    void notifiesOnce_whenEnteringANewPollingPhase() {
        when(syncMatchesUseCase.execute(any()))
                .thenReturn(Either.right(resultWithPhase("Kickoff in 45 minutes (soon)", NextSyncSchedule.Phase.SOON)))
                .thenReturn(Either.right(resultWithPhase("Kickoff in 40 minutes (soon)", NextSyncSchedule.Phase.SOON)))
                .thenReturn(Either.right(
                        resultWithPhase("Kickoff in 9 minutes (imminent)", NextSyncSchedule.Phase.IMMINENT)));

        scheduler.triggerManualSync();
        scheduler.triggerManualSync();
        scheduler.triggerManualSync();

        verify(adminNotificationService, times(1))
                .notifySyncScheduleChanged(any(), anyInt(), any(), eq("Kickoff in 45 minutes (soon)"));
        verify(adminNotificationService, times(1))
                .notifySyncScheduleChanged(any(), anyInt(), any(), eq("Kickoff in 9 minutes (imminent)"));
        verify(adminNotificationService, never())
                .notifySyncScheduleChanged(any(), anyInt(), any(), eq("Kickoff in 40 minutes (soon)"));
    }

    @Test
    void writesRoundLockedEvent_whenRoundStatusIsLocked() {
        when(syncMatchesUseCase.execute(any()))
                .thenReturn(Either.right(resultWithReason("Kickoff in 45 minutes (soon)", false)));

        scheduler.triggerManualSync();

        ArgumentCaptor<com.ligitabl.model.domain.OutboxEvent> captor =
                ArgumentCaptor.forClass(com.ligitabl.model.domain.OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        com.ligitabl.model.domain.OutboxEvent event = captor.getValue();
        assertEquals("ROUND_LOCKED", event.getEventType());
        assertEquals("round-locked:" + roundId, event.getIdempotencyKey());
        assertEquals("round", event.getAggregateType());
        assertEquals(roundId.toString(), event.getAggregateId());
    }

    @Test
    void doesNotWriteRoundLockedEvent_whenRoundIsNotLocked() {
        Season season = Season.builder()
                .id(seasonId)
                .mainContestId(UUID.randomUUID())
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(syncMatchesUseCase.execute(any())).thenReturn(Either.right(completeResult()));
        when(triggerFinalizationUseCase.execute(any()))
                .thenReturn(Either.left(
                        new TriggerRoundFinalizationUseCase.TriggerFinalizationError.RoundNotFound(roundId)));

        scheduler.triggerManualSync();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void repeatedLockedTicks_writeIdempotentEventEveryTime_relyingOnRepoToDedupe() {
        // MatchSyncScheduler itself has no in-memory "already saw LOCKED" tracking — it attempts
        // the write every tick while LOCKED, and relies entirely on OutboxRepo's ON CONFLICT DO
        // NOTHING (simulated here by the mock always accepting the call) for idempotency.
        when(syncMatchesUseCase.execute(any()))
                .thenReturn(Either.right(resultWithReason("Kickoff in 45 minutes (soon)", false)))
                .thenReturn(Either.right(resultWithReason("Kickoff in 40 minutes (soon)", false)));

        scheduler.triggerManualSync();
        scheduler.triggerManualSync();

        verify(outboxRepo, times(2)).save(any());
    }

    @Test
    void outboxWriteFailure_doesNotBreakSync() {
        when(syncMatchesUseCase.execute(any()))
                .thenReturn(Either.right(resultWithReason("Kickoff in 45 minutes (soon)", false)));
        when(outboxRepo.save(any())).thenThrow(new RuntimeException("db down"));

        scheduler.triggerManualSync();

        // Sync still completes normally (circuit breaker success, next tick scheduled) despite
        // the outbox write failing — the failure is caught and logged, never propagated.
        verify(circuitBreaker).recordSuccess();
        verify(circuitBreaker, never()).recordFailure();
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void failedSync_doesNotNotifyScheduleChange() {
        when(syncMatchesUseCase.execute(any()))
                .thenReturn(Either.left(new SyncMatchesUseCase.SyncMatchesError.HierarchyError(
                        UseCaseErrors.validation("Competition has no active season"))));

        scheduler.triggerManualSync();

        verify(adminNotificationService, never()).notifySyncScheduleChanged(any(), anyInt(), any(), any());
    }

    private MatchSyncResult resultWithReason(String reason, boolean shouldNotify) {
        return new MatchSyncResult(
                seasonId,
                roundId,
                5,
                RoundStatus.LOCKED,
                10,
                10,
                0,
                List.of(),
                false, // allMatchesComplete
                false,
                false,
                List.of(),
                NextSyncSchedule.seconds(90, reason, shouldNotify));
    }

    private MatchSyncResult resultWithPhase(String reason, NextSyncSchedule.Phase phase) {
        return new MatchSyncResult(
                seasonId,
                roundId,
                5,
                RoundStatus.LOCKED,
                10,
                10,
                0,
                List.of(),
                false, // allMatchesComplete
                false,
                false,
                List.of(),
                NextSyncSchedule.seconds(90, reason).withPhase(phase));
    }

    private MatchSyncResult completeResult() {
        return new MatchSyncResult(
                seasonId,
                roundId,
                5,
                RoundStatus.COMPLETED,
                10,
                10,
                10,
                List.of(),
                true, // allMatchesComplete
                false,
                false,
                List.of(),
                NextSyncSchedule.hours(6, "All matches complete"));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = MatchSyncScheduler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
