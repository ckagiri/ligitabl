package com.ligitabl.model.repo;

import java.util.UUID;

import com.ligitabl.model.domain.LeaderboardResponse;

/**
 * Repository for computing user contest leaderboards
 *
 * Aggregates user prediction results across rounds to calculate rankings.
 *
 * Tiebreaker order:
 * 1. total_score DESC (highest score wins)
 * 2. total_zeroes DESC (more perfect prediction wins)
 * 3. total_swaps ASC (fewer lineup changes wins)
 * 4. max_score DESC (higher best round wins)
 * 5. public_id ASC (unique identifier as final tiebreaker)
 */
public interface LeaderboardRepo {
    /**
     * Computes leaderboard for a contest within a specific round range with pagination.
     *
     * @param contestId The contest to compute leaderboard for
     * @param seasonId The season (for filtering round submissions)
     * @param fromRound Start of round range (inclusive)
     * @param toRound End of round range (inclusive)
     * @param userId Optional user to resolve ranking details for
     * @param offset Pagination offset (0-based)
     * @param limit Pagination size
     * @param activeOnly When true, exclude soft-removed members; when false, include them as former members
     * @return Paginated leaderboard response
     */
    LeaderboardResponse computeLeaderboard(
            UUID contestId, UUID seasonId, int fromRound, int toRound, UUID userId, int offset, int limit,
            boolean activeOnly);

    /**
     * Resolves the highest round position with finalized standings within a range.
     *
     * @param seasonId The season
     * @param fromRound Start of round range (inclusive)
     * @param toRound End of round range (inclusive)
     * @return The highest finalized round position, or null if none
     */
    Integer resolveEffectiveToRound(UUID seasonId, int fromRound, int toRound);
}
