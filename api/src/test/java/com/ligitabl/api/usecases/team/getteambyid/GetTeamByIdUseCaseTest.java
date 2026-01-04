package com.ligitabl.api.usecases.team.getteambyid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
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
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.repo.TeamRepo;

class GetTeamByIdUseCaseTest {

    @Mock
    RequestValidator validator;

    @Mock
    TeamRepo teamRepo;

    GetTeamByIdUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetTeamByIdUseCase(validator, teamRepo);
    }

    @Test
    void happy_path_returns_team_dto() {
        var id = UUID.randomUUID();
        var query = new GetTeamByIdQuery(id.toString());
        var team = Team.builder()
                .id(id)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(teamRepo.findById(id)).thenReturn(Optional.of(team));

        Either<UseCaseError, TeamDto> result = useCase.execute(query);

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight().getId()).isEqualTo(id);
        verify(validator).validate(query);
        verify(teamRepo).findById(id);
    }

    @Test
    void not_found_returns_left() {
        var id = UUID.randomUUID();
        var query = new GetTeamByIdQuery(id.toString());
        when(validator.validate(query)).thenReturn(Either.right(query));
        when(teamRepo.findById(id)).thenReturn(Optional.empty());

        var result = useCase.execute(query);
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
        verify(teamRepo).findById(id);
    }

    @Test
    void validation_failure_short_circuits() {
        var id = UUID.randomUUID().toString();
        var query = new GetTeamByIdQuery(id);
        UseCaseError error = new ValidationError("invalid id");
        when(validator.validate(query)).thenReturn(Either.left(error));

        var result = useCase.execute(query);
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(teamRepo);
    }
}
