package com.ligitabl.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class TeamRepoVisibilityTest {

    @Test
    void teamRepoClassIsLoadable() {
        assertDoesNotThrow(() -> Class.forName("com.ligitabl.model.repo.TeamRepo"));
    }
}
