package com.ligitabl.model.query;

/**
 * Leaderboard entry for a single user. Totals cover the selected phase.
 *
 * @param position current position in leaderboard (1 = first)
 * @param roundScore score for the latest finalized round in phase
 * @param maxScore highest single round score in phase
 * @param joinedAtGw gameweek where the user joined
 * @param movement position change from previous period (+ = up, - = down, 0 = same)
 * @param scored whether any rounds in this phase have been scored for this user
 * @param isFormerMember whether the user has been removed from the contest
 */
public record LeaderboardEntry(
        int position,
        String publicId,
        String displayName,
        int totalScore,
        int roundScore,
        int maxScore,
        int totalZeroes,
        int totalSwaps,
        Integer joinedAtGw,
        int movement,
        boolean scored,
        boolean isFormerMember) {}
