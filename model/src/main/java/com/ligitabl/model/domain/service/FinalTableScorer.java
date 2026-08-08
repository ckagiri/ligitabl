package com.ligitabl.model.domain.service;

import java.util.List;

import com.ligitabl.model.domain.FinalTableScore;
import com.ligitabl.model.domain.ScoringResult;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;

import lombok.RequiredArgsConstructor;

/**
 * Scores a Final Table prediction: the standard {@link ScoringEngine} distance score plus a flat
 * bonus per exactly-right position.
 *
 * <p>A thin wrapper rather than a change to {@link ScoringEngine}, so the main game's scoring stays
 * byte-identical and the bonus rule lives in exactly one place.
 */
@RequiredArgsConstructor
public class FinalTableScorer {

    /** Points awarded per exactly-right position, on top of the distance score. */
    public static final int ZERO_BONUS = 10;

    private final ScoringEngine scoringEngine;

    /**
     * @throws IllegalStateException propagated from {@link ScoringEngine} when a predicted team is
     *     absent from the standings, or the hit total exceeds {@code maxHitPoints}. Callers scoring
     *     a whole season must catch per row so one bad row cannot fail the pass.
     */
    public FinalTableScore score(List<TeamRank> predictions, List<StandingsTeamRank> standings, int maxHitPoints) {
        ScoringResult result = scoringEngine.calculateScore(predictions, standings, maxHitPoints);

        int bonusPoints = result.zeroesCount() * ZERO_BONUS;

        return new FinalTableScore(
                result.detailedRankings(),
                result.score(),
                result.zeroesCount(),
                bonusPoints,
                result.score() + bonusPoints);
    }
}
