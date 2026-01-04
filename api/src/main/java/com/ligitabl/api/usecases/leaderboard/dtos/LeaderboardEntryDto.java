package com.ligitabl.api.usecases.leaderboard.dtos;

public record LeaderboardEntryDto(
        int position,
        String displayName,
        int totalScore,
        int maxScore,
        int totalZeroes,
        int totalSwaps,
        int movement
) {}
