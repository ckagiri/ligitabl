package com.ligitabl.api.usecases.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.team.createteam.CreateTeamCommand;
import com.ligitabl.api.usecases.team.createteam.CreateTeamUseCase;
import com.ligitabl.api.usecases.team.createteam.TeamCreationGuard;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;

class CreateTeamUseCaseTest {

    @Mock
    RequestValidator validator;

    @Mock
    TeamCreationGuard creationGuard;

    @Mock
    TeamMapper mapper;

    @Mock
    TeamRepo teamRepo;

    CreateTeamUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new CreateTeamUseCase(validator, creationGuard, mapper, teamRepo);
    }

    @Test
    void happy_path_creates_and_returns_dto() {
        var cmd = CreateTeamCommand.builder()
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug("arsenal")
                .tla("ARS")
                .build();
        var candidate = Team.builder()
                .name(cmd.getName())
                .shortName(cmd.getShortName())
                .slug(cmd.getTeamSlug())
                .tla(cmd.getTla())
                .build();
        var persisted = Team.builder()
                .id(UUID.randomUUID())
                .name(cmd.getName())
                .shortName(cmd.getShortName())
                .slug(cmd.getTeamSlug())
                .tla(cmd.getTla())
                .build();

        when(validator.validate(cmd)).thenReturn(Either.right(cmd));
        when(mapper.toEntity(cmd)).thenReturn(candidate);
        when(creationGuard.validate(candidate)).thenReturn(Either.right(candidate));
        when(teamRepo.create(candidate)).thenReturn(persisted);

        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        var dto = result.getValue();
        assertThat(dto.getId()).isEqualTo(persisted.getId());
        assertThat(dto.getSlug()).isEqualTo("arsenal");

        // verify pipeline
        verify(validator).validate(cmd);
        verify(mapper).toEntity(cmd);
        verify(creationGuard).validate(candidate);
        verify(teamRepo).create(candidate);
    }

    @Test
    void validation_failure_propagates_left() {
        var cmd = CreateTeamCommand.builder()
                .name("")
                .shortName("")
                .slug("")
                .tla("AA")
                .build();
        UseCaseError error = new ValidationError("invalid payload");
        when(validator.validate(cmd)).thenReturn(Either.left(error));

        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getError()).isSameAs(error);
        verifyNoInteractions(mapper, creationGuard, teamRepo);
    }
}
