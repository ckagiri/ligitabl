package com.ligitabl.api.rest.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.api.scheduling.syncmatches.TriggerRoundFinalizationUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundResult;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * Ported from .art/testing/TriggerRoundFinalizationUseCaseTest.java.
 */
@ExtendWith(MockitoExtension.class)
class TriggerRoundFinalizationUseCaseTest {

    private static final String COMPETITION_CODE = "PL";

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private RoundSubmissionRepo roundSubmissionRepo;

    @Mock
    private FinalizeRoundUseCase finalizeRoundUseCase;

    @Mock
    private AdminNotificationService adminNotificationService;

    @Test
    void shouldTriggerFinalizationWhenNoBlockingMatches() {
        var useCase = new TriggerRoundFinalizationUseCase(
                seasonRepo, roundRepo, matchRepo, roundSubmissionRepo, finalizeRoundUseCase, adminNotificationService);

        var seasonId = UUID.randomUUID();
        var roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(UUID.randomUUID())
                .clientId(1)
                .name("2024/25")
                .slug(com.ligitabl.model.domain.SeasonSlug.of("2024-25"))
                .startDate(java.time.LocalDate.of(2024, 8, 1))
                .endDate(java.time.LocalDate.of(2025, 5, 31))
                .currentRoundId(roundId)
                .currentMatchDay(10)
                .build();

        var round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Round 10")
                .slug("round-10")
                .position(10)
                .build();

        when(seasonRepo.findActiveSeason(COMPETITION_CODE)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId))
                .thenReturn(List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.FINISHED)));
        when(roundSubmissionRepo.existsByRound(seasonId, 10)).thenReturn(true);

        when(finalizeRoundUseCase.execute(seasonId))
                .thenReturn(Either.right(new FinalizeRoundResult(roundId, 10, 10, 5, false, java.time.Instant.now())));

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().finalized()).isTrue();
        assertThat(result.get().blocked()).isFalse();

        verify(finalizeRoundUseCase).execute(seasonId);
        verify(adminNotificationService, never()).notifyBlockedFinalization(any(), anyInt(), anyList(), anyList());
    }

    @Test
    void shouldBlockFinalizationWhenCancelledMatchesExist() {
        var useCase = new TriggerRoundFinalizationUseCase(
                seasonRepo, roundRepo, matchRepo, roundSubmissionRepo, finalizeRoundUseCase, adminNotificationService);

        var seasonId = UUID.randomUUID();
        var roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(UUID.randomUUID())
                .clientId(1)
                .name("2024/25")
                .slug(com.ligitabl.model.domain.SeasonSlug.of("2024-25"))
                .startDate(java.time.LocalDate.of(2024, 8, 1))
                .endDate(java.time.LocalDate.of(2025, 5, 31))
                .currentRoundId(roundId)
                .currentMatchDay(10)
                .build();

        var round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Round 10")
                .slug("round-10")
                .position(10)
                .build();

        when(seasonRepo.findActiveSeason(COMPETITION_CODE)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId))
                .thenReturn(List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.CANCELLED)));
        when(roundSubmissionRepo.existsByRound(seasonId, 10)).thenReturn(true);

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft())
                .isInstanceOf(TriggerRoundFinalizationUseCase.TriggerFinalizationError.BlockedByMatches.class);

        verify(adminNotificationService).notifyBlockedFinalization(eq(roundId), eq(10), anyList(), anyList());
        verify(finalizeRoundUseCase, never()).execute(any());
    }

    @Test
    void shouldBlockFinalizationWhenSuspendedMatchesExist() {
        var useCase = new TriggerRoundFinalizationUseCase(
                seasonRepo, roundRepo, matchRepo, roundSubmissionRepo, finalizeRoundUseCase, adminNotificationService);

        var seasonId = UUID.randomUUID();
        var roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(UUID.randomUUID())
                .clientId(1)
                .name("2024/25")
                .slug(com.ligitabl.model.domain.SeasonSlug.of("2024-25"))
                .startDate(java.time.LocalDate.of(2024, 8, 1))
                .endDate(java.time.LocalDate.of(2025, 5, 31))
                .currentRoundId(roundId)
                .currentMatchDay(10)
                .build();

        var round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Round 10")
                .slug("round-10")
                .position(10)
                .build();

        when(seasonRepo.findActiveSeason(COMPETITION_CODE)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId))
                .thenReturn(List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.SUSPENDED)));
        when(roundSubmissionRepo.existsByRound(seasonId, 10)).thenReturn(true);

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isLeft()).isTrue();
        verify(adminNotificationService).notifyBlockedFinalization(any(), anyInt(), anyList(), anyList());
        verify(finalizeRoundUseCase, never()).execute(any());
    }

    @Test
    void shouldAllowFinalizationWithPostponedMatches() {
        var useCase = new TriggerRoundFinalizationUseCase(
                seasonRepo, roundRepo, matchRepo, roundSubmissionRepo, finalizeRoundUseCase, adminNotificationService);

        var seasonId = UUID.randomUUID();
        var roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(UUID.randomUUID())
                .clientId(1)
                .name("2024/25")
                .slug(com.ligitabl.model.domain.SeasonSlug.of("2024-25"))
                .startDate(java.time.LocalDate.of(2024, 8, 1))
                .endDate(java.time.LocalDate.of(2025, 5, 31))
                .currentRoundId(roundId)
                .currentMatchDay(10)
                .build();

        var round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Round 10")
                .slug("round-10")
                .position(10)
                .build();

        when(seasonRepo.findActiveSeason(COMPETITION_CODE)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId))
                .thenReturn(List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.POSTPONED)));
        when(roundSubmissionRepo.existsByRound(seasonId, 10)).thenReturn(true);

        when(finalizeRoundUseCase.execute(seasonId))
                .thenReturn(Either.right(new FinalizeRoundResult(roundId, 10, 10, 5, false, java.time.Instant.now())));

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().finalized()).isTrue();

        verify(finalizeRoundUseCase).execute(seasonId);
        verify(adminNotificationService, never()).notifyBlockedFinalization(any(), anyInt(), anyList(), anyList());
    }

    @Test
    void shouldHandleSeasonNotFound() {
        var useCase = new TriggerRoundFinalizationUseCase(
                seasonRepo, roundRepo, matchRepo, roundSubmissionRepo, finalizeRoundUseCase, adminNotificationService);

        when(seasonRepo.findActiveSeason(COMPETITION_CODE)).thenReturn(Optional.empty());

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft())
                .isInstanceOf(TriggerRoundFinalizationUseCase.TriggerFinalizationError.SeasonNotFound.class);
    }

    @Test
    void shouldHandleFinalizationFailure() {
        var useCase = new TriggerRoundFinalizationUseCase(
                seasonRepo, roundRepo, matchRepo, roundSubmissionRepo, finalizeRoundUseCase, adminNotificationService);

        var seasonId = UUID.randomUUID();
        var roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(UUID.randomUUID())
                .clientId(1)
                .name("2024/25")
                .slug(com.ligitabl.model.domain.SeasonSlug.of("2024-25"))
                .startDate(java.time.LocalDate.of(2024, 8, 1))
                .endDate(java.time.LocalDate.of(2025, 5, 31))
                .currentRoundId(roundId)
                .currentMatchDay(10)
                .build();

        var round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Round 10")
                .slug("round-10")
                .position(10)
                .build();

        when(seasonRepo.findActiveSeason(COMPETITION_CODE)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId))
                .thenReturn(List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.FINISHED)));
        when(roundSubmissionRepo.existsByRound(seasonId, 10)).thenReturn(true);

        when(finalizeRoundUseCase.execute(seasonId))
                .thenReturn(Either.left(
                        new FinalizeRoundError.RoundNotReady(roundId, "Round status is OPEN, expected COMPLETED")));

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft())
                .isInstanceOf(TriggerRoundFinalizationUseCase.TriggerFinalizationError.FinalizationFailed.class);
    }

    @Test
    void shouldSkipFinalizationWhenNoRoundSubmissions() {
        var useCase = new TriggerRoundFinalizationUseCase(
                seasonRepo, roundRepo, matchRepo, roundSubmissionRepo, finalizeRoundUseCase, adminNotificationService);

        var seasonId = UUID.randomUUID();
        var roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(UUID.randomUUID())
                .clientId(1)
                .name("2024/25")
                .slug(com.ligitabl.model.domain.SeasonSlug.of("2024-25"))
                .startDate(java.time.LocalDate.of(2024, 8, 1))
                .endDate(java.time.LocalDate.of(2025, 5, 31))
                .currentRoundId(roundId)
                .currentMatchDay(10)
                .build();

        var round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Round 10")
                .slug("round-10")
                .position(10)
                .build();

        when(seasonRepo.findActiveSeason(COMPETITION_CODE)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(roundSubmissionRepo.existsByRound(seasonId, 10)).thenReturn(false);

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().finalized()).isFalse();
        assertThat(result.get().blocked()).isFalse();
        verify(finalizeRoundUseCase, never()).execute(any());
        verify(adminNotificationService, never()).notifyBlockedFinalization(any(), anyInt(), anyList(), anyList());
    }

    private Match createMatch(MatchStatus status) {
        return Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .roundId(UUID.randomUUID())
                .slug("h-v-a")
                .status(status)
                .matchday(10)
                .build();
    }
}
