package com.ligitabl.model.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MatchTest {

    @Test
    void validMatchShouldCreate() {
        UUID home = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .homeTeamId(home)
                .awayTeamId(away)
                .score(Score.builder().homeGoals(2).awayGoals(1).build())
                .status(MatchStatus.FINISHED)
                .build();

        assertNotNull(match.getScore());
        assertEquals(2, match.getScore().getHomeGoals());
        assertEquals(1, match.getScore().getAwayGoals());
    }

    @Test
    void sameTeamIdsShouldBeAllowedButNonsensical() {
        UUID id = UUID.randomUUID();
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .homeTeamId(id)
                .awayTeamId(id)
                .status(MatchStatus.SCHEDULED)
                .build();

        assertEquals(id, match.getHomeTeamId());
        assertEquals(id, match.getAwayTeamId());
    }
}
