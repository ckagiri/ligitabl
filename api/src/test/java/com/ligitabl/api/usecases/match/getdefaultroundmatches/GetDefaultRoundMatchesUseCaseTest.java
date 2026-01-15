package com.ligitabl.api.usecases.match.getdefaultroundmatches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.match.MatchDto;
import com.ligitabl.api.usecases.match.MatchEnricher;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;

class GetDefaultRoundMatchesUseCaseTest {

    @Mock
    HierarchyValidator hierarchyValidator;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    MatchRepo matchRepo;

    @Mock
    MatchEnricher matchEnricher;

    GetDefaultRoundMatchesUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetDefaultRoundMatchesUseCase(matchRepo, matchEnricher, hierarchyValidator, competitionDefaults);
    }

    @Test
    void happy_path_returns_match_dtos() {
        UUID roundId = UUID.randomUUID();

        var season = Season.builder().id(UUID.randomUUID()).build();
        var round = Round.builder().id(roundId).position(1).build();

        var match = Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .status(MatchStatus.SCHEDULED)
                .build();

        when(hierarchyValidator.resolveHierarchy("premier-league", null))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(season, round)));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of(match));

        MatchDto dto = MatchDto.builder().roundId(roundId).build();
        when(matchEnricher.enrichWithTeams(List.of(match))).thenReturn(Either.right(List.of(dto)));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(GetDefaultRoundMatchesQuery.currentRound(null));

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).hasSize(1);
        assertThat(result.getRight().getFirst().getRoundId()).isEqualTo(roundId);
        verify(hierarchyValidator).resolveHierarchy("premier-league", null);
        verify(matchRepo).findByRoundId(roundId);
        verify(matchEnricher).enrichWithTeams(List.of(match));
    }

    @Test
    void missing_active_season_returns_validation_error() {
        when(hierarchyValidator.resolveHierarchy("premier-league", null))
                .thenReturn(Either.left(
                        com.ligitabl.api.shared.errors.UseCaseErrors.validation("Competition has no active season")));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(GetDefaultRoundMatchesQuery.currentRound(null));

        assertThat(result.isLeft()).isTrue();
        verify(hierarchyValidator).resolveHierarchy("premier-league", null);
        verifyNoInteractions(matchRepo, matchEnricher);
    }

    @Test
    void missing_current_round_returns_validation_error() {
        when(hierarchyValidator.resolveHierarchy("premier-league", null))
                .thenReturn(Either.left(
                        com.ligitabl.api.shared.errors.UseCaseErrors.validation("Season has no current round")));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(GetDefaultRoundMatchesQuery.currentRound(null));

        assertThat(result.isLeft()).isTrue();
        verify(hierarchyValidator).resolveHierarchy("premier-league", null);
        verifyNoInteractions(matchRepo, matchEnricher);
    }
}
