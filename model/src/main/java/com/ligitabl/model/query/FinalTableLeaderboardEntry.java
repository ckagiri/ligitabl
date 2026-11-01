package com.ligitabl.model.query;

import java.time.Instant;

/**
 * Leaderboard entry for the Final Table Predictor side game. Separate from
 * {@link LeaderboardEntry}, which is shaped around contests, entries, phases and per-round
 * submissions — none of which this game has.
 *
 * @param position position in the leaderboard (1 = first), distinct per row
 * @param totalScore base + bonus + champion bonus
 * @param baseScore ScoringEngine distance score
 * @param zeroesCount exactly-right positions
 * @param bonusPoints zeroes * FinalTableScorer.ZERO_BONUS. The champion bonus is inside totalScore
 *     but shown only on a player's own page
 * @param swapCount swaps made before the lock — displayed, but never ordered on
 * @param settledAt when the user settled on their table; the tiebreak after score and zeroes
 */
public record FinalTableLeaderboardEntry(
        int position,
        String publicId,
        String displayName,
        int totalScore,
        int baseScore,
        int zeroesCount,
        int bonusPoints,
        int swapCount,
        Instant settledAt) {}
