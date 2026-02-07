package com.ligitabl.api.rest.team.updateteam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.rest.team.TeamDto;
import com.ligitabl.api.rest.team.TeamMapper;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;

class UpdateTeamUseCaseTest {

    @Mock
    RequestValidator validator;

    @Mock
    TeamMapper mapper;

    @Mock
    TeamUpdateGuard updateGuard;

    @Mock
    TeamRepo teamRepo;

    UpdateTeamUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new UpdateTeamUseCase(validator, mapper, updateGuard, teamRepo);
    }

    @Test
    void happy_path_updates_and_returns_dto() {
        var cmd = UpdateTeamCommand.builder()
                .id(UUID.randomUUID().toString())
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug("arsenal")
                .tla("ARS")
                .build();

        var candidate = Team.builder()
                .id(cmd.getUuid())
                .name(cmd.getName())
                .shortName(cmd.getShortName())
                .slug(cmd.getTeamSlug())
                .tla(cmd.getTla())
                .build();
        var persisted = candidate;

        when(validator.validate(cmd)).thenReturn(Either.right(cmd));
        when(mapper.toEntity(cmd)).thenReturn(candidate);
        when(updateGuard.validate(candidate)).thenReturn(Either.right(candidate));
        when(teamRepo.update(candidate)).thenReturn(persisted);

        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        TeamDto dto = result.getRight();
        assertThat(dto.getId()).isEqualTo(persisted.getId());
        assertThat(dto.getSlug()).isEqualTo("arsenal");

        verify(validator).validate(cmd);
        verify(mapper).toEntity(cmd);
        verify(updateGuard).validate(candidate);
        verify(teamRepo).update(candidate);
    }

    @Test
    void validation_failure_propagates_left() {
        var cmd = UpdateTeamCommand.builder()
                .id(UUID.randomUUID().toString())
                .name("")
                .shortName("")
                .slug("")
                .tla("AA")
                .build();
        UseCaseError error = new ValidationError("invalid payload");
        when(validator.validate(cmd)).thenReturn(Either.left(error));

        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(mapper, updateGuard, teamRepo);
    }

    @Test
    void guard_failure_propagates_left() {
        var cmd = UpdateTeamCommand.builder()
                .id(UUID.randomUUID().toString())
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug("arsenal")
                .tla("ARS")
                .build();
        var candidate = Team.builder()
                .id(cmd.getUuid())
                .name(cmd.getName())
                .shortName(cmd.getShortName())
                .slug(cmd.getTeamSlug())
                .tla(cmd.getTla())
                .build();
        UseCaseError error = new ValidationError("guard blocked");

        when(validator.validate(cmd)).thenReturn(Either.right(cmd));
        when(mapper.toEntity(cmd)).thenReturn(candidate);
        when(updateGuard.validate(candidate)).thenReturn(Either.left(error));

        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(teamRepo);
    }
}
