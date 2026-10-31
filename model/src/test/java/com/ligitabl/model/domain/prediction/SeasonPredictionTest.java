package com.ligitabl.model.domain.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ligitabl.model.domain.league.RoundStatus;
import com.ligitabl.model.domain.league.TeamRank;

class SeasonPredictionTest {

    private static final int N = 10;
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    /** Same states as the api's SwapEligibilityConsistencyTest, round N open. */
    @ParameterizedTest(name = "{0} lastSwap={3}")
    @CsvSource({
        // path,        at, opening, lastSwap, swap,                   opening swap
        "pre-season,     0,  0,      none,     PreSeasonMergeRequired, PreSeasonMergeRequired",
        "legacy,         9,  0,      none,     FirstSwapFree,          OpeningWindowOpen",
        "legacy,         9,  0,      25h,      UseOpeningWindowFirst,  OpeningWindowOpen",
        "legacy at N,   10,  0,      25h,      CooldownElapsed,        OpeningAlreadyUsed",
        "joined N-1,     9,  9,      none,     FirstSwapFree,          OpeningWindowOpen",
        "joined N-1,     9,  9,      1h,       UseOpeningWindowFirst,  OpeningWindowOpen",
        "joined N,      10, 10,      none,     FirstSwapFree,          OpeningAlreadyUsed",
        "joined N,      10, 10,      1h,       CooldownActive,         OpeningAlreadyUsed",
        "joined N,      10, 10,      25h,      CooldownElapsed,        OpeningAlreadyUsed",
        "free swap N,   10,  9,      1h,       UseOpeningWindowFirst,  OpeningWindowOpen",
    })
    void eligibilityInAnOpenRound(String path, int at, int opening, String lastSwap, String swap, String openingSwap) {
        SeasonPrediction prediction = prediction(at, opening, lastSwapAt(lastSwap));

        assertThat(prediction
                        .swapEligibility(N, RoundStatus.OPEN, NOW)
                        .getClass()
                        .getSimpleName())
                .isEqualTo(swap);
        assertThat(prediction
                        .openingSwapEligibility(N, RoundStatus.OPEN)
                        .getClass()
                        .getSimpleName())
                .isEqualTo(openingSwap);
    }

    @Test
    void roundNotOpenComesFirst() {
        SeasonPrediction preSeason = prediction(0, 0, null);

        assertThat(preSeason.swapEligibility(N, RoundStatus.LOCKED, NOW))
                .isEqualTo(new SwapEligibility.RoundNotOpen(RoundStatus.LOCKED));
        assertThat(preSeason.openingSwapEligibility(N, RoundStatus.LOCKED))
                .isEqualTo(new SwapEligibility.RoundNotOpen(RoundStatus.LOCKED));
    }

    @Test
    void cooldownReportsWhenTheNextSwapIsDue() {
        Instant lastSwapAt = NOW.minus(Duration.ofHours(1));

        assertThat(prediction(N, N, lastSwapAt).swapEligibility(N, RoundStatus.OPEN, NOW))
                .isEqualTo(new SwapEligibility.CooldownActive(lastSwapAt.plus(SeasonPrediction.SWAP_COOLDOWN)));
    }

    @Test
    void cooldownEndsExactlyAtTwentyFourHours() {
        Instant lastSwapAt = NOW.minus(SeasonPrediction.SWAP_COOLDOWN);

        assertThat(prediction(N, N, lastSwapAt).swapEligibility(N, RoundStatus.OPEN, NOW))
                .isInstanceOf(SwapEligibility.CooldownElapsed.class);
    }

    @Test
    void swapTargetsTheNextRoundOnlyWhenJoinedForIt() {
        assertThat(prediction(N + 1, N + 1, null).swapTargetRound(N)).isEqualTo(N + 1);
        assertThat(prediction(N, N, null).swapTargetRound(N)).isEqualTo(N);
        assertThat(prediction(N - 1, N - 1, null).swapTargetRound(N)).isEqualTo(N);
    }

    @Test
    void swapExchangesPositionsAndStartsTheCooldown() {
        SeasonPrediction prediction = prediction(N - 1, N - 1, null);

        SwapChange change = prediction.swap(new TeamSwap("ARS", "MCI"), N, RoundStatus.OPEN, NOW);

        assertThat(prediction.getCurrentRankings())
                .containsExactly(TeamRank.of("ARS", 3), TeamRank.of("LIV", 2), TeamRank.of("MCI", 1));
        assertThat(change).isEqualTo(new SwapChange(NOW, "ARS:1→3", "MCI:3→1"));
        assertThat(prediction.getSwaps()).containsExactly(new RoundSwap(N, List.of(change)));
        assertThat(prediction.getLastSwapAt()).isEqualTo(NOW);
        assertThat(prediction.getAtRoundNumber()).isEqualTo(N);
        assertThat(prediction.getOpeningCommittedRound()).isEqualTo(N - 1);
    }

    @Test
    void freeSwapLeavesTheOpeningWindowOpen() {
        SeasonPrediction prediction = prediction(N - 1, N - 1, null);

        prediction.swap(new TeamSwap("ARS", "LIV"), N, RoundStatus.OPEN, NOW);

        assertThat(prediction.openingSwapEligibility(N, RoundStatus.OPEN))
                .isInstanceOf(SwapEligibility.OpeningWindowOpen.class);
    }

    @Test
    void swapRefusesWhenNotEligible() {
        SeasonPrediction prediction = prediction(N, N, NOW.minus(Duration.ofHours(1)));

        assertThatThrownBy(() -> prediction.swap(new TeamSwap("ARS", "LIV"), N, RoundStatus.OPEN, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(prediction.getCurrentRankings()).isEqualTo(rankings());
    }

    @Test
    void openingSwapsApplyInOrderAndCommitTheWindow() {
        SeasonPrediction prediction = prediction(N - 1, N - 1, NOW.minus(Duration.ofHours(1)));

        List<SwapChange> changes = prediction.applyOpeningSwaps(
                List.of(new TeamSwap("ARS", "LIV"), new TeamSwap("ARS", "MCI")), N, RoundStatus.OPEN, NOW);

        assertThat(prediction.getCurrentRankings())
                .containsExactly(TeamRank.of("ARS", 3), TeamRank.of("LIV", 1), TeamRank.of("MCI", 2));
        assertThat(changes).extracting(SwapChange::teamA).containsExactly("ARS:1→2", "ARS:2→3");
        assertThat(prediction.getOpeningCommittedRound()).isEqualTo(N);
        assertThat(prediction.getLastSwapAt()).isEqualTo(NOW);
        assertThat(prediction.getAtRoundNumber()).isEqualTo(N);
        assertThat(prediction.openingSwapEligibility(N, RoundStatus.OPEN))
                .isInstanceOf(SwapEligibility.OpeningAlreadyUsed.class);
    }

    @Test
    void openingSwapsRefuseOnceTheWindowIsUsed() {
        SeasonPrediction prediction = prediction(N, N, null);

        assertThatThrownBy(() ->
                        prediction.applyOpeningSwaps(List.of(new TeamSwap("ARS", "LIV")), N, RoundStatus.OPEN, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mergeKeepsTheFirstSwapFreeWhenNoSwapWasMade() {
        SeasonPrediction prediction = prediction(0, 0, null);
        List<TeamRank> merged = List.of(TeamRank.of("LIV", 1), TeamRank.of("ARS", 2), TeamRank.of("MCI", 3));

        prediction.mergePreSeason(N, merged, List.of(), NOW);

        assertThat(prediction.getAtRoundNumber()).isEqualTo(N);
        assertThat(prediction.getOpeningCommittedRound()).isEqualTo(N);
        assertThat(prediction.getCurrentRankings()).isEqualTo(merged);
        assertThat(prediction.getLastSwapAt()).isNull();
    }

    @Test
    void mergeSpendsTheFirstSwapWhenThePreSeasonRowHadSwaps() {
        SeasonPrediction prediction = prediction(0, 0, null);
        prediction.addSwap(0, new SwapChange(NOW.minus(Duration.ofDays(30)), "ARS:1→2", "LIV:2→1"));

        prediction.mergePreSeason(N, rankings(), List.of(), NOW);

        assertThat(prediction.getLastSwapAt()).isEqualTo(NOW);
    }

    @Test
    void mergeSpendsTheFirstSwapWhenTheMergeSwaps() {
        SeasonPrediction prediction = prediction(0, 0, null);
        SwapChange change = new SwapChange(NOW, "ARS:1→2", "LIV:2→1");

        prediction.mergePreSeason(N, rankings(), List.of(change), NOW);

        assertThat(prediction.getLastSwapAt()).isEqualTo(NOW);
        assertThat(prediction.getSwaps()).containsExactly(new RoundSwap(N, List.of(change)));
    }

    @Test
    void mergeRefusesAnAlreadyMergedPrediction() {
        SeasonPrediction prediction = prediction(N, N, null);

        assertThatThrownBy(() -> prediction.mergePreSeason(N, rankings(), List.of(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Instant lastSwapAt(String ago) {
        return switch (ago) {
            case "none" -> null;
            case "1h" -> NOW.minus(Duration.ofHours(1));
            case "25h" -> NOW.minus(Duration.ofHours(25));
            default -> throw new IllegalArgumentException(ago);
        };
    }

    private static List<TeamRank> rankings() {
        return List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3));
    }

    private static SeasonPrediction prediction(int at, int opening, Instant lastSwapAt) {
        return SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .currentRankings(rankings())
                .swaps(new ArrayList<>())
                .lastSwapAt(lastSwapAt)
                .atRoundNumber(at)
                .openingCommittedRound(opening)
                .build();
    }
}
