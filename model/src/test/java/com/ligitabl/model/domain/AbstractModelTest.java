package com.ligitabl.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AbstractModelTest {

    @Test
    void sameIdIsTheSameEntityWhateverTheOtherFields() {
        UUID id = UUID.randomUUID();

        assertThat(team(id, "Arsenal")).isEqualTo(team(id, "Arsenal FC"));
        assertThat(team(id, "Arsenal")).hasSameHashCodeAs(team(id, "Arsenal FC"));
        assertThat(team(id, "Arsenal")).isNotEqualTo(team(UUID.randomUUID(), "Arsenal"));
    }

    @Test
    void unsavedEntityEqualsOnlyItself() {
        Team unsaved = team(null, "Arsenal");

        assertThat(unsaved).isEqualTo(unsaved);
        assertThat(unsaved).isNotEqualTo(team(null, "Arsenal"));
    }

    @Test
    void differentEntityTypesNeverMatch() {
        UUID id = UUID.randomUUID();
        Round round = Round.builder()
                .id(id)
                .seasonId(UUID.randomUUID())
                .name("Round 1")
                .slug("round-1")
                .build();

        assertThat(team(id, "Arsenal")).isNotEqualTo(round);
    }

    private static Team team(UUID id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .shortName("ARS")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();
    }
}
