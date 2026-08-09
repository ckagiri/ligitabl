package com.ligitabl.model.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.FinalTableScore;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;

class FinalTableScorerTest {

    /**
     * 20 teams: max_hit_points = 2 * (20/2)^2 = 200, so a perfect table scores 200 base + 200 zero
     * bonus + 25 champion = 425.
     */
    private static final int TEAM_COUNT = 20;

    private static final int MAX_HIT_POINTS = 200;

    private final FinalTableScorer scorer = new FinalTableScorer(new ScoringEngine());

    private static List<TeamRank> table(List<String> codes) {
        List<TeamRank> ranks = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            ranks.add(TeamRank.of(codes.get(i), i + 1));
        }
        return ranks;
    }

    private static List<StandingsTeamRank> standings(List<String> codes) {
        List<StandingsTeamRank> ranks = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            ranks.add(new StandingsTeamRank(
                    TeamRank.of(codes.get(i), i + 1),
                    StandingsMetadata.builder().build()));
        }
        return ranks;
    }

    private static List<String> codes(int count) {
        List<String> codes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            codes.add(String.format("T%02d", i));
        }
        return codes;
    }

    @Test
    void perfectPredictionScoresFourHundredAndTwentyFive() {
        List<String> order = codes(TEAM_COUNT);

        FinalTableScore score = scorer.score(table(order), standings(order), MAX_HIT_POINTS);

        assertThat(score.baseScore()).isEqualTo(200);
        assertThat(score.zeroesCount()).isEqualTo(20);
        assertThat(score.bonusPoints()).isEqualTo(200);
        assertThat(score.championBonus()).isEqualTo(25);
        assertThat(score.totalScore()).isEqualTo(425);
        assertThat(score.totalScore()).isEqualTo(FinalTableScorer.maxScore(MAX_HIT_POINTS, TEAM_COUNT));
    }

    @Test
    void exactlyReversedPredictionScoresZero() {
        List<String> actual = codes(TEAM_COUNT);
        List<String> predicted = new ArrayList<>(actual);
        Collections.reverse(predicted);

        FinalTableScore score = scorer.score(table(predicted), standings(actual), MAX_HIT_POINTS);

        // Total distance for a fully reversed 20-team table is exactly max_hit_points (200),
        // so the base score bottoms out and no position is exactly right.
        assertThat(score.baseScore()).isZero();
        assertThat(score.zeroesCount()).isZero();
        assertThat(score.bonusPoints()).isZero();
        assertThat(score.championBonus()).isZero();
        assertThat(score.totalScore()).isZero();
    }

    @Test
    void handCheckedMidCase() {
        List<String> actual = codes(TEAM_COUNT);

        // Swap the top two and the bottom two; everything else is exactly right.
        List<String> predicted = new ArrayList<>(actual);
        Collections.swap(predicted, 0, 1);
        Collections.swap(predicted, 18, 19);

        FinalTableScore score = scorer.score(table(predicted), standings(actual), MAX_HIT_POINTS);

        // Four teams are off by one: total hit = 4, base = 200 - 4 = 196.
        assertThat(score.baseScore()).isEqualTo(196);
        // The other sixteen are exact.
        assertThat(score.zeroesCount()).isEqualTo(16);
        assertThat(score.bonusPoints()).isEqualTo(160);
        // Swapping the top two means the champion pick is wrong.
        assertThat(score.championBonus()).isZero();
        assertThat(score.totalScore()).isEqualTo(356);
    }

    @Test
    void bonusIsExactlyTenPerZero() {
        List<String> actual = codes(TEAM_COUNT);

        // Rotating the whole table by one leaves nothing in place.
        List<String> predicted = new ArrayList<>(actual);
        Collections.rotate(predicted, 1);

        FinalTableScore score = scorer.score(table(predicted), standings(actual), MAX_HIT_POINTS);

        assertThat(score.zeroesCount()).isZero();
        assertThat(score.bonusPoints()).isZero();
        assertThat(score.bonusPoints()).isEqualTo(score.zeroesCount() * FinalTableScorer.ZERO_BONUS);
        assertThat(score.totalScore())
                .isEqualTo(score.baseScore() + score.bonusPoints() + score.championBonus());
    }

    @Test
    void championBonusStacksOnTopOfThatClubsZeroBonus() {
        List<String> actual = codes(TEAM_COUNT);

        // T01 stays 1st; swap two clubs in mid-table so the rest is not a perfect table.
        List<String> predicted = new ArrayList<>(actual);
        Collections.swap(predicted, 9, 10);

        FinalTableScore score = scorer.score(table(predicted), standings(actual), MAX_HIT_POINTS);

        // Two clubs off by one: base = 200 - 2 = 198, and the other eighteen are exact.
        assertThat(score.baseScore()).isEqualTo(198);
        assertThat(score.zeroesCount()).isEqualTo(18);
        // The champion is counted twice on purpose: once as an ordinary zero, once as the champion.
        assertThat(score.bonusPoints()).isEqualTo(180);
        assertThat(score.championBonus()).isEqualTo(25);
        assertThat(score.totalScore()).isEqualTo(403);
    }

    @Test
    void noChampionBonusWhenTheTitlePickFinishesSecond() {
        List<String> actual = codes(TEAM_COUNT);

        // T02 predicted 1st, finishes 2nd — close, but the champion bonus is all or nothing.
        List<String> predicted = new ArrayList<>(actual);
        Collections.swap(predicted, 0, 1);

        FinalTableScore score = scorer.score(table(predicted), standings(actual), MAX_HIT_POINTS);

        assertThat(score.championBonus()).isZero();
        assertThat(score.totalScore()).isEqualTo(score.baseScore() + score.bonusPoints());
    }

    @Test
    void championBonusIsAwardedEvenWhenNothingElseIsRight() {
        List<String> actual = codes(TEAM_COUNT);

        // Right champion, then reverse everything below it. Reversing an odd-length tail (19
        // clubs) pins its midpoint, so that club is exact too — two zeroes, not one.
        List<String> predicted = new ArrayList<>(actual);
        List<String> tail = predicted.subList(1, predicted.size());
        Collections.reverse(tail);

        FinalTableScore score = scorer.score(table(predicted), standings(actual), MAX_HIT_POINTS);

        assertThat(score.zeroesCount()).isEqualTo(2);
        assertThat(score.bonusPoints()).isEqualTo(2 * FinalTableScorer.ZERO_BONUS);
        assertThat(score.championBonus()).isEqualTo(FinalTableScorer.CHAMPION_BONUS);
        assertThat(score.totalScore())
                .isEqualTo(score.baseScore() + score.bonusPoints() + FinalTableScorer.CHAMPION_BONUS);
    }

    @Test
    void emptyPredictionScoresNoChampionBonusRatherThanThrowing() {
        // The season pass isolates faults per row; a table with no 1st place must not be what
        // fails it.
        FinalTableScore score = scorer.score(List.of(), standings(codes(TEAM_COUNT)), MAX_HIT_POINTS);

        assertThat(score.championBonus()).isZero();
        assertThat(score.totalScore()).isEqualTo(MAX_HIT_POINTS);
    }

    @Test
    void maxScoreMatchesWhatAPerfectTableCanActuallyProduce() {
        assertThat(FinalTableScorer.maxScore(MAX_HIT_POINTS, TEAM_COUNT)).isEqualTo(425);
        // A smaller league must not be quoted 425.
        assertThat(FinalTableScorer.maxScore(50, 10)).isEqualTo(50 + 100 + 25);
    }

    @Test
    void resultRankingsCarryPredictedAndActualPositions() {
        List<String> actual = codes(TEAM_COUNT);
        List<String> predicted = new ArrayList<>(actual);
        Collections.swap(predicted, 0, 3); // T01 predicted 4th, T04 predicted 1st

        FinalTableScore score = scorer.score(table(predicted), standings(actual), MAX_HIT_POINTS);

        assertThat(score.resultRankings()).hasSize(TEAM_COUNT);

        var t04 = score.resultRankings().stream()
                .filter(r -> r.getRanking().getCode().equals("T04"))
                .findFirst()
                .orElseThrow();
        assertThat(t04.getRanking().getPosition()).isEqualTo(1);
        assertThat(t04.getStandingsPosition()).isEqualTo(4);
        assertThat(t04.getHit()).isEqualTo(3);
    }

    @Test
    void propagatesWhenPredictedTeamIsMissingFromStandings() {
        // Callers scoring a whole season must catch this per row rather than fail the pass.
        List<String> actual = codes(TEAM_COUNT);
        List<String> predicted = new ArrayList<>(actual);
        predicted.set(0, "GONE");

        assertThatThrownBy(() -> scorer.score(table(predicted), standings(actual), MAX_HIT_POINTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GONE");
    }
}
