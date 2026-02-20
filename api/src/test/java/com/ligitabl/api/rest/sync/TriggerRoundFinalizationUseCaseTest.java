package com.ligitabl.api.rest.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundResult;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.api.scheduling.syncmatches.TriggerRoundFinalizationUseCase;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundRepo;
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
    private FinalizeRoundUseCase finalizeRoundUseCase;

    @Test
    void shouldTriggerFinalizationWhenSubmissionsExist() {
        var useCase = new TriggerRoundFinalizationUseCase(seasonRepo, roundRepo, finalizeRoundUseCase);

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

        when(finalizeRoundUseCase.execute(seasonId))
                .thenReturn(Either.right(new FinalizeRoundResult(roundId, 10, 10, 5, false, java.time.Instant.now())));

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().finalized()).isTrue();
        assertThat(result.get().blocked()).isFalse();

        verify(finalizeRoundUseCase).execute(seasonId);
    }

    @Test
    void shouldAllowFinalizationWithPostponedMatches() {
        var useCase = new TriggerRoundFinalizationUseCase(seasonRepo, roundRepo, finalizeRoundUseCase);

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

        when(finalizeRoundUseCase.execute(seasonId))
                .thenReturn(Either.right(new FinalizeRoundResult(roundId, 10, 10, 5, false, java.time.Instant.now())));

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().finalized()).isTrue();

        verify(finalizeRoundUseCase).execute(seasonId);
    }

    @Test
    void shouldHandleSeasonNotFound() {
        var useCase = new TriggerRoundFinalizationUseCase(seasonRepo, roundRepo, finalizeRoundUseCase);

        when(seasonRepo.findActiveSeason(COMPETITION_CODE)).thenReturn(Optional.empty());

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft())
                .isInstanceOf(TriggerRoundFinalizationUseCase.TriggerFinalizationError.SeasonNotFound.class);
    }

    @Test
    void shouldHandleFinalizationFailure() {
        var useCase = new TriggerRoundFinalizationUseCase(seasonRepo, roundRepo, finalizeRoundUseCase);

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

        when(finalizeRoundUseCase.execute(seasonId))
                .thenReturn(Either.left(
                        new FinalizeRoundError.RoundNotReady(roundId, "Round status is OPEN, expected COMPLETED")));

        var result = useCase.execute(new TriggerRoundFinalizationUseCase.TriggerFinalizationCommand(COMPETITION_CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft())
                .isInstanceOf(TriggerRoundFinalizationUseCase.TriggerFinalizationError.FinalizationFailed.class);
    }
}
