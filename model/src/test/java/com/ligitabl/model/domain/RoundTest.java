package com.ligitabl.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RoundTest {

    @Test
    void shouldReturnCompletedWhenMatchesNull() {
        var round = createRound();

        assertThat(round.computeStatus(null)).isEqualTo(RoundStatus.COMPLETED);
    }

    @Test
    void shouldReturnCompletedWhenNoMatches() {
        var round = createRound();

        assertThat(round.computeStatus(List.of())).isEqualTo(RoundStatus.COMPLETED);
    }

    @Test
    void shouldReturnOpenWhenAllMatchesScheduled() {
        var round = createRound();
        var matches = List.of(createMatch(MatchStatus.SCHEDULED), createMatch(MatchStatus.SCHEDULED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.OPEN);
    }

    @Test
    void shouldReturnLockedWhenSomeMatchesFinishedAndSomeScheduled() {
        var round = createRound();
        var matches = List.of(
                createMatch(MatchStatus.FINISHED),
                createMatch(MatchStatus.FINISHED),
                createMatch(MatchStatus.SCHEDULED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.LOCKED);
    }

    @Test
    void shouldReturnLockedWhenAnyMatchLive() {
        var round = createRound();
        var matches = List.of(createMatch(MatchStatus.SCHEDULED), createMatch(MatchStatus.LIVE));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.LOCKED);
    }

    @Test
    void shouldReturnLockedWhenAnyMatchSuspended() {
        var round = createRound();
        var matches = List.of(createMatch(MatchStatus.POSTPONED), createMatch(MatchStatus.SUSPENDED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.LOCKED);
    }

    @Test
    void shouldReturnLockedWhenAnyMatchCancelled() {
        var round = createRound();
        var matches = List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.CANCELLED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.LOCKED);
    }

    @Test
    void shouldReturnCompletedWhenAllMatchesFinished() {
        var round = createRound();
        var matches = List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.FINISHED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.COMPLETED);
    }

    @Test
    void shouldReturnCompletedWhenAllMatchesPostponed() {
        var round = createRound();
        var matches = List.of(createMatch(MatchStatus.POSTPONED), createMatch(MatchStatus.POSTPONED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.COMPLETED);
    }

    @Test
    void shouldReturnCompletedWhenFinishedAndPostponedButNoScheduled() {
        var round = createRound();
        var matches = List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.POSTPONED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.COMPLETED);
    }

    @Test
    void shouldReturnOpenWhenScheduledAndPostponed() {
        var round = createRound();
        var matches = List.of(createMatch(MatchStatus.SCHEDULED), createMatch(MatchStatus.POSTPONED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.OPEN);
    }

    @Test
    void shouldReturnAdvancedRegardlessOfMatches() {
        var round = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .name("Round 1")
                .slug("round-1")
                .position(1)
                .advanced(true)
                .build();
        var matches = List.of(createMatch(MatchStatus.SCHEDULED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.ADVANCED);
    }

    @Test
    void shouldReturnFinalizedRegardlessOfMatches() {
        var round = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .name("Round 1")
                .slug("round-1")
                .position(1)
                .finalized(true)
                .build();
        var matches = List.of(createMatch(MatchStatus.SCHEDULED));

        assertThat(round.computeStatus(matches)).isEqualTo(RoundStatus.FINALIZED);
    }

    @Test
    void computeMatchStatus_staticVariant_matchesInstanceMethod_whenNotAdvancedOrFinalized() {
        var matches = List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.SCHEDULED));

        assertThat(Round.computeMatchStatus(matches)).isEqualTo(RoundStatus.LOCKED);
    }

    @Test
    void computeMatchStatus_staticVariant_ignoresAdvancedAndFinalized() {
        // Unlike the instance method, the static matches-only variant has no Round to short-circuit
        // on advanced/finalized — it always classifies purely from the match list.
        var matches = List.of(createMatch(MatchStatus.FINISHED), createMatch(MatchStatus.FINISHED));

        assertThat(Round.computeMatchStatus(matches)).isEqualTo(RoundStatus.COMPLETED);
    }

    @Test
    void computeMatchStatus_staticVariant_returnsCompletedForNullOrEmpty() {
        assertThat(Round.computeMatchStatus(null)).isEqualTo(RoundStatus.COMPLETED);
        assertThat(Round.computeMatchStatus(List.of())).isEqualTo(RoundStatus.COMPLETED);
    }

    private static Round createRound() {
        return Round.builder()
                .id(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .name("Round 1")
                .slug("round-1")
                .position(1)
                .finalized(false)
                .build();
    }

    private static Match createMatch(MatchStatus status) {
        return Match.builder().id(UUID.randomUUID()).status(status).build();
    }
}
