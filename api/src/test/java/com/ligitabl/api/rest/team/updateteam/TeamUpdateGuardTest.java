package com.ligitabl.api.rest.team.updateteam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.repo.TeamRepo;

class TeamUpdateGuardTest {
    @Mock
    TeamRepo teamRepo;

    TeamUpdateGuard guard;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        guard = new TeamUpdateGuard(teamRepo);
    }

    @Test
    void passes_when_id_present_exists_and_slug_not_in_use_by_others() {
        UUID id = UUID.randomUUID();
        Team candidate = Team.builder()
                .id(id)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        when(teamRepo.existsById(id)).thenReturn(true);
        when(teamRepo.isSlugInUseByAnotherTeam(TeamSlug.of("arsenal"), id)).thenReturn(false);

        var result = guard.validate(candidate);
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isSameAs(candidate);
        verify(teamRepo).existsById(id);
        verify(teamRepo).isSlugInUseByAnotherTeam(TeamSlug.of("arsenal"), id);
    }

    @Test
    void fails_when_id_missing() {
        Team candidate = Team.builder()
                .id(null)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        var result = guard.validate(candidate);
        assertThat(result.isLeft()).isTrue();
        verifyNoInteractions(teamRepo);
    }

    @Test
    void fails_when_team_not_found() {
        UUID id = UUID.randomUUID();
        Team candidate = Team.builder()
                .id(id)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        when(teamRepo.existsById(id)).thenReturn(false);

        var result = guard.validate(candidate);
        assertThat(result.isLeft()).isTrue();
        verify(teamRepo).existsById(id);
        verify(teamRepo, never()).isSlugInUseByAnotherTeam(any(), any());
    }

    @Test
    void fails_when_slug_in_use_by_other_team() {
        UUID id = UUID.randomUUID();
        Team candidate = Team.builder()
                .id(id)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        when(teamRepo.existsById(id)).thenReturn(true);
        when(teamRepo.isSlugInUseByAnotherTeam(TeamSlug.of("arsenal"), id)).thenReturn(true);

        var result = guard.validate(candidate);
        assertThat(result.isLeft()).isTrue();
        verify(teamRepo).existsById(id);
        verify(teamRepo).isSlugInUseByAnotherTeam(TeamSlug.of("arsenal"), id);
    }
}
