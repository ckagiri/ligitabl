package com.ligitabl.model.domain.prediction;

import java.util.List;

/**
 * The outcome of scoring one Final Table prediction.
 *
 * @param resultRankings per-team predicted position, actual position and distance
 * @param baseScore ScoringEngine distance score (maxHitPoints - total hit)
 * @param zeroesCount positions predicted exactly right
 * @param bonusPoints zeroes * FinalTableScorer.ZERO_BONUS
 * @param championBonus FinalTableScorer.CHAMPION_BONUS when the club placed 1st finished 1st, else
 *     0. Kept out of bonusPoints so that one stays exactly zeroes * ZERO_BONUS
 * @param totalScore baseScore + bonusPoints + championBonus
 */
public record FinalTableScore(
        List<ResultTeamRank> resultRankings,
        int baseScore,
        int zeroesCount,
        int bonusPoints,
        int championBonus,
        int totalScore) {}
