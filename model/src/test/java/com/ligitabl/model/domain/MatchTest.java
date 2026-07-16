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
    void unfinishingInSetupMode_clearsTheRecordedScore() {
        for (MatchStatus target : new MatchStatus[] {MatchStatus.SCHEDULED, MatchStatus.POSTPONED}) {
            Match match = matchWithStatus(MatchStatus.FINISHED);
            match.setScore(2, 1);

            match.transitionTo(target, "setup mode correction", true);

            assertEquals(target, match.getStatus());
            assertNull(match.getScore(), "score must be cleared when un-finishing to " + target);
        }
    }

    @Test
    void transitionMatrix_shouldMatchExpectedAllowedTransitions() {
        Map<MatchStatus, EnumSet<MatchStatus>> allowed = new EnumMap<>(MatchStatus.class);
        allowed.put(
                MatchStatus.SCHEDULED,
                EnumSet.of(MatchStatus.LIVE, MatchStatus.FINISHED, MatchStatus.POSTPONED, MatchStatus.CANCELLED));
        allowed.put(MatchStatus.LIVE, EnumSet.of(MatchStatus.SUSPENDED, MatchStatus.FINISHED));
        allowed.put(MatchStatus.SUSPENDED, EnumSet.of(MatchStatus.CANCELLED, MatchStatus.LIVE, MatchStatus.POSTPONED));
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

    @Test
    void setupMode_onlyAllowsScheduledPostponedFinishedAsTargets_regardlessOfCurrentStatus() {
        EnumSet<MatchStatus> setupModeTargets =
                EnumSet.of(MatchStatus.SCHEDULED, MatchStatus.POSTPONED, MatchStatus.FINISHED);

        for (MatchStatus from : MatchStatus.values()) {
            for (MatchStatus to : MatchStatus.values()) {
                Match match = matchWithStatus(from);

                if (from == to) {
                    assertDoesNotThrow(() -> match.transitionTo(to, "noop", true));
                    assertEquals(from, match.getStatus());
                    continue;
                }

                if (setupModeTargets.contains(to)) {
                    assertDoesNotThrow(() -> match.transitionTo(to, "setup mode correction", true));
                    assertEquals(to, match.getStatus());
                } else {
                    assertThrows(
                            IllegalStateException.class, () -> match.transitionTo(to, "setup mode correction", true));
                    assertEquals(from, match.getStatus());
                }
            }
        }
    }

    @Test
    void validTransitionsFrom_inSetupMode_isFixedToScheduledPostponedFinished() {
        for (MatchStatus status : MatchStatus.values()) {
            EnumSet<MatchStatus> expected =
                    EnumSet.of(MatchStatus.SCHEDULED, MatchStatus.POSTPONED, MatchStatus.FINISHED);
            expected.remove(status);

            assertEquals(expected, EnumSet.copyOf(Match.validTransitionsFrom(status, true)));
        }
    }

    @Test
    void isComplete_isTrueOnlyForFinishedAndPostponed() {
        EnumSet<MatchStatus> complete = EnumSet.of(MatchStatus.FINISHED, MatchStatus.POSTPONED);

        for (MatchStatus status : MatchStatus.values()) {
            assertEquals(complete.contains(status), matchWithStatus(status).isComplete());
        }
    }

    @Test
    void isBlocking_isTrueOnlyForCancelledAndSuspended() {
        EnumSet<MatchStatus> blocking = EnumSet.of(MatchStatus.CANCELLED, MatchStatus.SUSPENDED);

        for (MatchStatus status : MatchStatus.values()) {
            assertEquals(blocking.contains(status), matchWithStatus(status).isBlocking());
        }
    }

    @Test
    void isComplete_and_isBlocking_areMutuallyExclusive() {
        for (MatchStatus status : MatchStatus.values()) {
            Match match = matchWithStatus(status);
            assertFalse(match.isComplete() && match.isBlocking());
        }
    }
}
