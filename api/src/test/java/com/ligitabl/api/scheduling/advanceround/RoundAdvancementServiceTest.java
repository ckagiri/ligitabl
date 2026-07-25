package com.ligitabl.api.scheduling.advanceround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

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
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoundAdvancementServiceTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private AdminNotificationService adminNotificationService;

    @Mock
    private OutboxRepo outboxRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RoundAdvancementService service;

    private UUID roundId;
    private UUID seasonId;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws Exception {
        roundId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        service = new RoundAdvancementService(
                taskScheduler, roundRepo, seasonRepo, clock, adminNotificationService, outboxRepo, objectMapper);
        setField(service, "delayMinutes", 3);
    }

    private RoundFinalizedOutboxAssertion assertRoundFinalizedOutboxEvent() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(OutboxEventTypes.ROUND_ADVANCED);
        return new RoundFinalizedOutboxAssertion(event);
    }

    private record RoundFinalizedOutboxAssertion(OutboxEvent event) {
        void hasPayload(int roundPosition, int currentRoundPosition) throws Exception {
            var payload = new ObjectMapper()
                    .readValue(
                            event.getPayload(),
                            com.ligitabl.api.notification.outbox.RoundAdvancedPayload.class);
            assertThat(payload.roundPosition()).isEqualTo(roundPosition);
            assertThat(payload.currentRoundPosition()).isEqualTo(currentRoundPosition);
        }
    }

    @Test
    void scheduleAdvancement_notifiesPreAdvancementWarning() {
        Season season = season(38);
        Round round = round(5, true, null, false);
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        service.scheduleAdvancement(roundId, seasonId);

        verify(adminNotificationService)
                .notifyAdvancementScheduled(eq(roundId), eq(5), any(OffsetDateTime.class), eq(3));
    }

    @Test
    void attemptAutoAdvancement_success_notifiesRoundAdvanced() throws Exception {
        Season season = season(38);
        Round round = round(5, true, now().plusMinutes(3), false);
        Round nextRound = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(6)
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 6)).thenReturn(Optional.of(nextRound));

        service.attemptAutoAdvancement(roundId, seasonId);

        verify(adminNotificationService).notifyRoundAdvanced(5, seasonId, false);
        verify(adminNotificationService, never()).notifyAdvancementFailed(any(), anyString());
        // Enqueued at advance time (not finalize time) — currentRoundPosition is the newly
        // opened next round, since only advanced rounds count toward leaderboard placements.
        assertRoundFinalizedOutboxEvent().hasPayload(5, 6);
    }

    @Test
    void attemptAutoAdvancement_lastRound_notifiesWithLastRoundFlag() throws Exception {
        Season season = season(38);
        Round round = round(38, true, now().plusMinutes(3), false);
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        service.attemptAutoAdvancement(roundId, seasonId);

        verify(adminNotificationService).notifyRoundAdvanced(38, seasonId, true);
        // No next round to open — currentRoundPosition stays pinned at the final round itself.
        assertRoundFinalizedOutboxEvent().hasPayload(38, 38);
    }

    @Test
    void attemptAutoAdvancement_cancelled_noNotification() {
        Round round = round(5, true, null, false); // advanceAt cleared = cancelled
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        service.attemptAutoAdvancement(roundId, seasonId);

        verify(adminNotificationService, never()).notifyRoundAdvanced(anyInt(), any(), anyBoolean());
        verify(adminNotificationService, never()).notifyAdvancementFailed(any(), anyString());
    }

    @Test
    void attemptAutoAdvancement_failure_notifiesAdvancementFailed() {
        Round round = round(5, true, now().plusMinutes(3), false);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.empty()); // triggers IllegalStateException

        service.attemptAutoAdvancement(roundId, seasonId);

        verify(adminNotificationService).notifyAdvancementFailed(eq(roundId), anyString());
    }

    @Test
    void advanceManually_notifiesRoundAdvanced() throws Exception {
        Season season = season(38);
        Round round = round(5, true, null, false);
        Round nextRound = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(6)
                .build();
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 6)).thenReturn(Optional.of(nextRound));

        service.advanceManually(roundId);

        verify(adminNotificationService).notifyRoundAdvanced(5, seasonId, false);
        assertRoundFinalizedOutboxEvent().hasPayload(5, 6);
    }

    @Test
    void advanceManually_outboxFailureDoesNotBlockAdvancement() {
        Season season = season(38);
        Round round = round(5, true, null, false);
        Round nextRound = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(6)
                .build();
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 6)).thenReturn(Optional.of(nextRound));
        when(outboxRepo.save(any())).thenThrow(new RuntimeException("db down"));

        boolean advanced = service.advanceManually(roundId);

        assertThat(advanced).isTrue();
        verify(seasonRepo).save(season);
        verify(adminNotificationService).notifyRoundAdvanced(5, seasonId, false);
    }

    private Season season(int maxRounds) {
        return Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .maxRounds(maxRounds)
                .build();
    }

    private Round round(int position, boolean finalized, OffsetDateTime advanceAt, boolean advanced) {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(position)
                .finalized(finalized)
                .advanceAt(advanceAt)
                .advanced(advanced)
                .build();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = RoundAdvancementService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
