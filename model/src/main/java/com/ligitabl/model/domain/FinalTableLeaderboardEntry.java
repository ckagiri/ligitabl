package com.ligitabl.model.domain;

import java.time.Instant;

/**
 * Leaderboard entry for the Final Table Predictor side game.
 *
 * <p>Separate from {@link LeaderboardEntry}, which is shaped around contests, entries, phases and
 * per-round submissions — none of which this game has.
 *
 * @param position Position in the leaderboard (1 = first), distinct per row
 * @param publicId User's unique public identifier
 * @param displayName User's display name
 * @param totalScore base + bonus, max 400
 * @param baseScore ScoringEngine distance score, max 200
 * @param zeroesCount Exactly-right positions
 * @param bonusPoints zeroes * 10
 * @param swapCount Swaps made before the lock — displayed, but never ordered on
 * @param settledAt When the user settled on their table; the tiebreak after score and zeroes
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
