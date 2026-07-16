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

import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.scheduling.advanceround.RoundAdvancementService;
import com.ligitabl.api.scheduling.resilience.MatchSyncCircuitBreaker;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Season;
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

    private MatchSyncResult completeResult() {
        return new MatchSyncResult(
                seasonId,
                roundId,
                5,
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
