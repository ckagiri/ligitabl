package com.ligitabl.api.usecases.team.getteambyslug;

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

class GetTeamBySlugUseCaseTest {

    @Mock
    TeamRepo teamRepo;

    @Mock
    RequestValidator validator;

    GetTeamBySlugUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetTeamBySlugHandler(teamRepo, validator);
    }

    @Test
    void happy_path_returns_team_dto() {
        var query = new GetTeamBySlugQuery("arsenal");
        var team = Team.builder()
                .id(UUID.randomUUID())
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(teamRepo.findBySlug(TeamSlug.of("arsenal"))).thenReturn(Optional.of(team));

        Either<UseCaseError, TeamDto> result = useCase.execute(query);

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight().getSlug()).isEqualTo("arsenal");
        verify(validator).validate(query);
        verify(teamRepo).findBySlug(TeamSlug.of("arsenal"));
    }

    @Test
    void not_found_returns_left() {
        var query = new GetTeamBySlugQuery("unknown");
        when(validator.validate(query)).thenReturn(Either.right(query));
        when(teamRepo.findBySlug(TeamSlug.of("unknown"))).thenReturn(Optional.empty());

        var result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
        verify(teamRepo).findBySlug(TeamSlug.of("unknown"));
    }

    @Test
    void validation_failure_short_circuits() {
        var query = new GetTeamBySlugQuery("bad");
        UseCaseError error = new ValidationError("invalid slug");
        when(validator.validate(query)).thenReturn(Either.left(error));

        var result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(teamRepo);
    }
}
