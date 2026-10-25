package com.ligitabl.api.rest.leaderboard;

public record LeaderboardEntryDto(
        int position,
        String publicId,
        String displayName,
        int totalScore,
        int roundScore,
        int maxScore,
        int totalZeroes,
        int totalSwaps,
        Integer joinedAtGw,
        int movement) {}
