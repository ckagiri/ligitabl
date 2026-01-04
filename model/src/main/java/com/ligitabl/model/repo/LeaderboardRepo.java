package com.ligitabl.model.repo;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.LeaderboardEntry;

/**
 * Repository for computing user contest leaderboards
 *
 * Aggregates user prediction results across rounds to calculate rankings.
 *
 * Tiebreaker order:
 * 1. total_score DESC (highest score wins)
 * 2. total_zeroes DESC (more perfect predictions wins)
 * 3. total_swaps ASC (fewer lineup changes wins)
 * 4. max_score DESC (higher best round wins)
 * 5. display_name ASC (alphabetical as final tiebreaker)
 */
public interface LeaderboardRepo {
    /**
     * Computes leaderboard for a contest within a specific round range
     *
     * @param contestId The contest to compute leaderboard for
     * @param seasonId The season (for filtering round submissions)
     * @param fromRound Start of round range (inclusive)
     * @param toRound End of round range (inclusive)
     * @return List of leaderboard entries sorted by tiebreaker rules
     */
    List<LeaderboardEntry> computeLeaderboard(UUID contestId, UUID seasonId, int fromRound, int toRound);
}
