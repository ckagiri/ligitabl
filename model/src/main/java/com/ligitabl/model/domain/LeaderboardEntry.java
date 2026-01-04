package com.ligitabl.model.domain;

/**
 * Leaderboard entry for a single user
 *
 * @param position Current position in leaderboard (1 = first)
 * @param displayName User's display name
 * @param totalScore Total score across all rounds in phase
 * @param maxScore Highest single round score in phase
 * @param totalZeroes Total perfect predictions (zeroes) in phase
 * @param totalSwaps Total lineup changes (swaps) in phase
 * @param movement Position change from previous period (+ = up, - = down, 0 = same)
 */
public record LeaderboardEntry(
        int position,
        String displayName,
        int totalScore,
        int maxScore,
        int totalZeroes,
        int totalSwaps,
        int movement) {}
