package com.ligitabl.model.domain;

import java.util.List;

/**
 * The outcome of scoring one Final Table prediction.
 *
 * @param resultRankings Per-team predicted position, actual position and distance
 * @param baseScore ScoringEngine distance score (maxHitPoints - total hit)
 * @param zeroesCount Positions predicted exactly right
 * @param bonusPoints zeroes * FinalTableScorer.ZERO_BONUS
 * @param totalScore baseScore + bonusPoints
 */
public record FinalTableScore(
        List<ResultTeamRank> resultRankings, int baseScore, int zeroesCount, int bonusPoints, int totalScore) {}
