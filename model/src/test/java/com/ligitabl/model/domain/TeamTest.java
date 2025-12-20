package com.ligitabl.model.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TeamTest {

    @Test
    void validTeamShouldCreate() {
        UUID id = UUID.randomUUID();
        Team team = Team.builder()
                .id(id)
                .name("Manchester City")
                .shortName("Man City")
                .slug(TeamSlug.of("man-city"))
                .tla("MCI")
                .build();

        assertEquals(id, team.getId());
        assertEquals("Manchester City", team.getName());
        assertEquals("MCI", team.getTla());
        assertEquals("Man City", team.getShortName());
    }

    @Test
    void nullIdIsAllowedByBuilderButSlugRequired() {
        assertThrows(IllegalArgumentException.class, () -> Team.builder()
                .name("Name")
                .shortName("Short")
                .tla("TLA")
                .slug(TeamSlug.of(""))
                .build());
    }
}
