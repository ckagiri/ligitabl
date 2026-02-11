package com.ligitabl.api.rest.leaderboard.getleaderboard;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.RoundSpan;

public record GetLeaderboardResult(
        UUID contestId,
        RoundSpan phase,
        List<LeaderboardEntry> rankings,
        List<RoundSpan> allPhases,
        LeaderboardEntry userEntry,
        boolean userInCurrentPage,
        int userPageOffset,
        int totalParticipants,
        boolean hasNext,
        boolean hasPrevious,
        int offset,
        int limit) {}
