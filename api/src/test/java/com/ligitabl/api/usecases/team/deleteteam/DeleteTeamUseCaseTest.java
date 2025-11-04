package com.ligitabl.api.usecases.team.deleteteam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.Unit;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.model.repo.TeamRepo;

class DeleteTeamUseCaseTest {

    @Mock
    RequestValidator validator;

    @Mock
    TeamRepo teamRepo;

    DeleteTeamUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new DeleteTeamHandler(validator, teamRepo);
    }

    @Test
    void happy_path_deletes_and_returns_unit() {
        var id = UUID.randomUUID().toString();
        var cmd = DeleteTeamCommand.of(id);

        when(validator.validate(cmd)).thenReturn(Either.right(cmd));
        when(teamRepo.existsById(cmd.getUuid())).thenReturn(true);

        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(Unit.INSTANCE);
        verify(teamRepo).delete(cmd.getUuid());
    }

    @Test
    void not_found_returns_left() {
        var id = UUID.randomUUID().toString();
        var cmd = DeleteTeamCommand.of(id);
        when(validator.validate(cmd)).thenReturn(Either.right(cmd));
        when(teamRepo.existsById(cmd.getUuid())).thenReturn(false);

        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
        verify(teamRepo, never()).delete(any());
    }

    @Test
    void validation_failure_short_circuits() {
        var id = UUID.randomUUID().toString();
        var cmd = DeleteTeamCommand.of(id);
        UseCaseError error = new ValidationError("invalid id");
        when(validator.validate(cmd)).thenReturn(Either.left(error));

        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(teamRepo);
    }
}
