package com.ligitabl.model.domain;

import java.util.List;

/**
 * Paginated leaderboard response with optional user position info.
 */
public record LeaderboardResponse(
        List<LeaderboardEntry> entries,
        LeaderboardEntry userEntry,
        boolean userInCurrentPage,
        int userPageOffset,
        int totalParticipants,
        boolean hasNext,
        boolean hasPrevious,
        Integer effectiveToRound) {}
