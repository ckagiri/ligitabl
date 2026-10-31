package com.ligitabl.api.usecase.match.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;

class MatchUpdateHelperTest {

    private static final OffsetDateTime KICKOFF = OffsetDateTime.of(2026, 10, 24, 14, 0, 0, 0, ZoneOffset.UTC);

    private final MatchUpdateHelper helper = new MatchUpdateHelper();

    /** The 2026-10-24 incident: football-data reported FINISHED, then reverted to TIMED with null scores. */
    @Test
    void ignoresApiRegressionFromFinishedToTimed() {
        var match = finishedMatch(2, 1);

        boolean changed = helper.applyUpdate(match, feedMatch(MatchStatus.SCHEDULED, null, null));

        assertThat(changed).isFalse();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(match.getScore().getHomeGoals()).isEqualTo(2);
        assertThat(match.getScore().getAwayGoals()).isEqualTo(1);
    }

    @Test
    void ignoresApiRegressionFromFinishedToInPlay() {
        var match = finishedMatch(2, 1);

        assertThat(helper.applyUpdate(match, feedMatch(MatchStatus.LIVE, 1, 1))).isFalse();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(match.getScore().getHomeGoals()).isEqualTo(2);
    }

    @Test
    void appliesScoreCorrectionWhileApiStillReportsFinished() {
        var match = finishedMatch(2, 1);

        assertThat(helper.applyUpdate(match, feedMatch(MatchStatus.FINISHED, 2, 2)))
                .isTrue();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(match.getScore().getAwayGoals()).isEqualTo(2);
    }

    @Test
    void stillAppliesPostponedMatchBeingRescheduled() {
        var match = baseMatch(MatchStatus.POSTPONED);

        assertThat(helper.applyUpdate(match, feedMatch(MatchStatus.SCHEDULED, null, null)))
                .isTrue();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
    }

    private Match finishedMatch(int home, int away) {
        var match = baseMatch(MatchStatus.FINISHED);
        match.recordScore(home, away);
        return match;
    }

    private Match baseMatch(MatchStatus status) {
        return Match.builder()
                .id(UUID.randomUUID())
                .clientId(560616)
                .roundId(UUID.randomUUID())
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(status)
                .kickOff(KICKOFF)
                .matchday(8)
                .build();
    }

    private FeedMatch feedMatch(MatchStatus status, Integer home, Integer away) {
        return new FeedMatch(560616L, status, KICKOFF, 8, null, null, home, away);
    }
}
