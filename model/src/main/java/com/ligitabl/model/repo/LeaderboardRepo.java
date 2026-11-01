package com.ligitabl.model.repo;

import java.util.UUID;

import com.ligitabl.model.query.LeaderboardResponse;

/**
 * Contest leaderboards, aggregated from round results.
 *
 * Tiebreaker order:
 * 1. total_score DESC
 * 2. total_zeroes DESC
 * 3. total_swaps ASC
 * 4. max_score DESC
 * 5. public_id ASC (unique, so no ties remain)
 */
public interface LeaderboardRepo {
    /**
     * @param fromRound start of round range (inclusive)
     * @param toRound end of round range (inclusive)
     * @param userId optional user to resolve ranking details for
     * @param activeOnly when true, exclude soft-removed members; when false, include them as former members
     */
    LeaderboardResponse computeLeaderboard(
            UUID contestId,
            UUID seasonId,
            int fromRound,
            int toRound,
            UUID userId,
            int offset,
            int limit,
            boolean activeOnly);

    /** The highest round position with finalized standings within the inclusive range, or null if none. */
    Integer resolveEffectiveToRound(UUID seasonId, int fromRound, int toRound);
}
