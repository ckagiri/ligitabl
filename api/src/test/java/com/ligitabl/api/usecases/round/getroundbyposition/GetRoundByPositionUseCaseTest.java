package com.ligitabl.api.usecases.round.getroundbyposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
import com.ligitabl.api.usecases.round.RoundDto;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Round;

class GetRoundByPositionUseCaseTest {

    @Mock
    HierarchyValidator hierarchyValidator;

    @Mock
    RequestValidator validator;

    GetRoundByPositionHandler useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetRoundByPositionHandler(hierarchyValidator, validator);
    }

    @Test
    void happy_path_returns_round_dto() {
        var query = new GetRoundByPositionQuery("premier-league", "2024-25", 1);

        var round = Round.builder()
                .id(UUID.randomUUID())
                .name("Matchday 1")
                .position(1)
                .build();

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(hierarchyValidator.validateCompetitionSeasonAndRound("premier-league", "2024-25", 1))
                .thenReturn(Either.right(round));

        Either<UseCaseError, RoundDto> result = useCase.execute(query);

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight().getPosition()).isEqualTo(1);
        verify(validator).validate(query);
        verify(hierarchyValidator).validateCompetitionSeasonAndRound("premier-league", "2024-25", 1);
    }

    @Test
    void not_found_round_returns_left() {
        var query = new GetRoundByPositionQuery("premier-league", "2024-25", 99);

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(hierarchyValidator.validateCompetitionSeasonAndRound("premier-league", "2024-25", 99))
                .thenReturn(Either.left(new NotFoundError("Round", "position", "99")));

        Either<UseCaseError, RoundDto> result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
        verify(hierarchyValidator).validateCompetitionSeasonAndRound("premier-league", "2024-25", 99);
        verifyNoMoreInteractions(hierarchyValidator);
    }

    @Test
    void validation_failure_short_circuits() {
        var query = new GetRoundByPositionQuery("premier-league", "bad", 1);
        UseCaseError error = new ValidationError("invalid slug");

        when(validator.validate(query)).thenReturn(Either.left(error));

        Either<UseCaseError, RoundDto> result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(hierarchyValidator);
    }
}
