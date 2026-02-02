package com.ligitabl.api.rest.team.createteam;

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

class TeamCreationGuardTest {

    @Mock
    TeamRepo teamRepo;

    TeamCreationGuard guard;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        guard = new TeamCreationGuard(teamRepo);
    }

    @Test
    void passes_when_id_null_and_slug_unique() {
        Team candidate = Team.builder()
                .id(null)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        when(teamRepo.existsBySlug(TeamSlug.of("arsenal"))).thenReturn(false);

        var result = guard.validate(candidate);
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isSameAs(candidate);

        verify(teamRepo).existsBySlug(TeamSlug.of("arsenal"));
    }

    @Test
    void fails_when_id_present() {
        Team candidate = Team.builder()
                .id(UUID.randomUUID())
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
    void fails_when_slug_already_exists() {
        Team candidate = Team.builder()
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        when(teamRepo.existsBySlug(TeamSlug.of("arsenal"))).thenReturn(true);

        var result = guard.validate(candidate);
        assertThat(result.isLeft()).isTrue();
        verify(teamRepo).existsBySlug(TeamSlug.of("arsenal"));
    }
}
