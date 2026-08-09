package com.ligitabl.model.domain;

import java.util.List;

/**
 * The outcome of scoring one Final Table prediction.
 *
 * @param resultRankings Per-team predicted position, actual position and distance
 * @param baseScore ScoringEngine distance score (maxHitPoints - total hit)
 * @param zeroesCount Positions predicted exactly right
 * @param bonusPoints zeroes * FinalTableScorer.ZERO_BONUS
 * @param championBonus FinalTableScorer.CHAMPION_BONUS when the club placed 1st finished 1st, else
 *     0. Held apart from bonusPoints rather than folded in, so "zeroes × 10" stays true of
 *     bonusPoints and the two rules can be shown as the separate things they are
 * @param totalScore baseScore + bonusPoints + championBonus
 */
public record FinalTableScore(
        List<ResultTeamRank> resultRankings,
        int baseScore,
        int zeroesCount,
        int bonusPoints,
        int championBonus,
        int totalScore) {}
