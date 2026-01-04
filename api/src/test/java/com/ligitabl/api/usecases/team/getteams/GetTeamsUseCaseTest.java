package com.ligitabl.api.usecases.team.getteams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.repo.TeamRepo;

class GetTeamsUseCaseTest {

    @Mock
    TeamRepo teamRepo;

    GetTeamsUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetTeamsUseCase(teamRepo);
    }

    @Test
    void returns_all_teams_mapped_to_dto() {
        var t1 = Team.builder()
                .id(UUID.randomUUID())
                .name("Arsenal")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();
        var t2 = Team.builder()
                .id(UUID.randomUUID())
                .name("Chelsea")
                .shortName("Chelsea")
                .slug(TeamSlug.of("chelsea"))
                .tla("CHE")
                .build();

        when(teamRepo.findAll()).thenReturn(List.of(t1, t2));

        Either<UseCaseError, List<TeamDto>> result = useCase.execute(null);

        assertThat(result.isRight()).isTrue();
        var list = result.get();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getSlug()).isEqualTo("arsenal");
        assertThat(list.get(1).getSlug()).isEqualTo("chelsea");
        verify(teamRepo).findAll();
    }
}
