package com.ligitabl.model.domain.service;

import java.util.List;

import com.ligitabl.model.domain.FinalTableScore;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.ScoringResult;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;

import lombok.RequiredArgsConstructor;

/**
 * Scores a Final Table prediction: the standard {@link ScoringEngine} distance score, a flat bonus
 * per exactly-right position, and a further bonus for calling the champion.
 *
 * <p>A thin wrapper rather than a change to {@link ScoringEngine}, so the main game's scoring stays
 * byte-identical and the bonus rules live in exactly one place.
 */
@RequiredArgsConstructor
public class FinalTableScorer {

    /** Points awarded per exactly-right position, on top of the distance score. */
    public static final int ZERO_BONUS = 10;

    /**
     * Points awarded when the club placed 1st actually finishes 1st. Stacks on that club's {@link
     * #ZERO_BONUS} rather than replacing it, so the champion pick is worth {@code ZERO_BONUS +
     * CHAMPION_BONUS} while every other exact call stays worth {@code ZERO_BONUS}.
     */
    public static final int CHAMPION_BONUS = 25;

    private final ScoringEngine scoringEngine;

    /**
     * The best score obtainable in a season: the distance ceiling, a zero on every club, and the
     * champion called right.
     *
     * <p>Here rather than at each call site because it is the scoring rule read backwards — three
     * pages quote this number and they must never disagree with what {@link #score} can produce.
     */
    public static int maxScore(int maxHitPoints, int teamCount) {
        return maxHitPoints + teamCount * ZERO_BONUS + CHAMPION_BONUS;
    }

    /**
     * @throws IllegalStateException propagated from {@link ScoringEngine} when a predicted team is
     *     absent from the standings, or the hit total exceeds {@code maxHitPoints}. Callers scoring
     *     a whole season must catch per row so one bad row cannot fail the pass.
     */
    public FinalTableScore score(List<TeamRank> predictions, List<StandingsTeamRank> standings, int maxHitPoints) {
        ScoringResult result = scoringEngine.calculateScore(predictions, standings, maxHitPoints);

        int bonusPoints = result.zeroesCount() * ZERO_BONUS;
        int championBonus = championBonus(result.detailedRankings());

        return new FinalTableScore(
                result.detailedRankings(),
                result.score(),
                result.zeroesCount(),
                bonusPoints,
                championBonus,
                result.score() + bonusPoints + championBonus);
    }

    /**
     * Read off the scored rows rather than the raw input: the row placed 1st is the claim, and its
     * {@code standingsPosition} is where that club actually finished.
     *
     * <p>Returns 0 for a prediction with no 1st place at all. A malformed row is the caller's
     * problem to log, not this method's to throw on — the season pass isolates faults per row and
     * this must not be what fails a whole table.
     */
    private int championBonus(List<ResultTeamRank> resultRankings) {
        if (resultRankings == null) {
            return 0;
        }

        return resultRankings.stream()
                        .anyMatch(row -> row.getRanking() != null
                                && row.getRanking().getPosition() == 1
                                && row.getStandingsPosition() == 1)
                ? CHAMPION_BONUS
                : 0;
    }
}
