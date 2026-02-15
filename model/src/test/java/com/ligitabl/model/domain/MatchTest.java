package com.ligitabl.model.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MatchTest {

    private static Match matchWithStatus(MatchStatus status) {
        return Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .roundId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(status)
                .build();
    }

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

    @Test
    void transitionMatrix_shouldMatchExpectedAllowedTransitions() {
        Map<MatchStatus, EnumSet<MatchStatus>> allowed = new EnumMap<>(MatchStatus.class);
        allowed.put(
                MatchStatus.SCHEDULED,
                EnumSet.of(
                        MatchStatus.SCHEDULED,
                        MatchStatus.LIVE,
                        MatchStatus.FINISHED,
                        MatchStatus.POSTPONED,
                        MatchStatus.CANCELLED));
        allowed.put(MatchStatus.LIVE, EnumSet.of(MatchStatus.SUSPENDED, MatchStatus.FINISHED));
        allowed.put(MatchStatus.SUSPENDED, EnumSet.of(MatchStatus.CANCELLED));
        allowed.put(MatchStatus.POSTPONED, EnumSet.of(MatchStatus.SCHEDULED, MatchStatus.CANCELLED));
        allowed.put(
                MatchStatus.CANCELLED, EnumSet.of(MatchStatus.SCHEDULED, MatchStatus.POSTPONED, MatchStatus.FINISHED));
        allowed.put(MatchStatus.FINISHED, EnumSet.noneOf(MatchStatus.class));

        for (MatchStatus from : MatchStatus.values()) {
            for (MatchStatus to : MatchStatus.values()) {
                Match match = matchWithStatus(from);

                if (from == to) {
                    assertDoesNotThrow(() -> match.transitionTo(to, "noop"));
                    assertEquals(from, match.getStatus());
                    continue;
                }

                boolean shouldAllow = allowed.get(from).contains(to);
                if (shouldAllow) {
                    assertDoesNotThrow(() -> match.transitionTo(to, "reason"));
                    assertEquals(to, match.getStatus());

                    if (to == MatchStatus.POSTPONED) {
                        assertTrue(match.wasPostponed());
                    }
                    if (to == MatchStatus.SUSPENDED) {
                        assertTrue(match.isWasSuspended());
                    }
                } else {
                    assertThrows(IllegalStateException.class, () -> match.transitionTo(to, "reason"));
                    assertEquals(from, match.getStatus());
                }
            }
        }
    }
}
