package com.ligitabl.api.scheduling.advanceround;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.TaskScheduler;

import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
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

    private RoundAdvancementService service;

    private UUID roundId;
    private UUID seasonId;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws Exception {
        roundId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        service = new RoundAdvancementService(taskScheduler, roundRepo, seasonRepo, clock, adminNotificationService);
        setField(service, "delayMinutes", 3);
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
    void attemptAutoAdvancement_success_notifiesRoundAdvanced() {
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
    }

    @Test
    void attemptAutoAdvancement_lastRound_notifiesWithLastRoundFlag() {
        Season season = season(38);
        Round round = round(38, true, now().plusMinutes(3), false);
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        service.attemptAutoAdvancement(roundId, seasonId);

        verify(adminNotificationService).notifyRoundAdvanced(38, seasonId, true);
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
    void advanceManually_notifiesRoundAdvanced() {
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
