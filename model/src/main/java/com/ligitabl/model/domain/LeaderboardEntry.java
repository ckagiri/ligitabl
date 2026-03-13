package com.ligitabl.model.domain;

/**
 * Leaderboard entry for a single user
 *
 * @param position Current position in leaderboard (1 = first)
 * @param publicId User's unique public identifier
 * @param displayName User's display name
 * @param totalScore Total score across all rounds in phase
 * @param roundScore Score for the latest finalized round in phase
 * @param maxScore Highest single round score in phase
 * @param totalZeroes Total perfect prediction (zeroes) in phase
 * @param totalSwaps Total lineup changes (swaps) in phase
 * @param joinedAtGw Gameweek where the user joined (season prediction atRoundNumber)
 * @param movement Position change from previous period (+ = up, - = down, 0 = same)
 * @param scored Whether any rounds in this phase have been scored for this user
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
        boolean scored) {}
