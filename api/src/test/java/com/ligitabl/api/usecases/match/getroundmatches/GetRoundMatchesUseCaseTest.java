package com.ligitabl.api.usecases.match.getroundmatches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.match.MatchDto;
import com.ligitabl.api.usecases.match.MatchEnricher;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.repo.MatchRepo;

class GetRoundMatchesUseCaseTest {

    @Mock
    MatchRepo matchRepo;

    @Mock
    RequestValidator validator;

    @Mock
    HierarchyValidator hierarchyValidator;

    @Mock
    MatchEnricher matchEnricher;

    GetRoundMatchesUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetRoundMatchesHandler(hierarchyValidator, matchRepo, matchEnricher, validator);
    }

    @Test
    void happy_path_returns_match_dtos() {
        var query = new GetRoundMatchesQuery("premier-league", "2024-25", 1);

        UUID roundId = UUID.randomUUID();

        var round = Round.builder().id(roundId).name("Matchday 1").position(1).build();

        var match = Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .status(MatchStatus.SCHEDULED)
                .build();

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(hierarchyValidator.validateCompetitionSeasonAndRound("premier-league", "2024-25", 1))
                .thenReturn(Either.right(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of(match));

        MatchDto dto = MatchDto.builder().roundId(roundId).build();
        when(matchEnricher.enrichWithTeams(List.of(match))).thenReturn(Either.right(List.of(dto)));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(query);

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).hasSize(1);
        assertThat(result.getRight().getFirst().getRoundId()).isEqualTo(roundId);
        verify(validator).validate(query);
        verify(hierarchyValidator).validateCompetitionSeasonAndRound("premier-league", "2024-25", 1);
        verify(matchRepo).findByRoundId(roundId);
        verify(matchEnricher).enrichWithTeams(List.of(match));
    }

    @Test
    void not_found_round_returns_left() {
        var query = new GetRoundMatchesQuery("premier-league", "2024-25", 99);

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(hierarchyValidator.validateCompetitionSeasonAndRound("premier-league", "2024-25", 99))
                .thenReturn(Either.left(new NotFoundError("Round", "position", "99")));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
        verify(hierarchyValidator).validateCompetitionSeasonAndRound("premier-league", "2024-25", 99);
        verifyNoInteractions(matchRepo);
    }

    @Test
    void validation_failure_short_circuits() {
        var query = new GetRoundMatchesQuery("premier-league", "bad", 1);
        UseCaseError error = new ValidationError("invalid slug");

        when(validator.validate(query)).thenReturn(Either.left(error));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(hierarchyValidator, matchRepo);
    }
}
