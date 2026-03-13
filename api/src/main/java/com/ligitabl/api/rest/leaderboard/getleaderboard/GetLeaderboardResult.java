package com.ligitabl.api.rest.leaderboard.getleaderboard;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.RoundSpan;

public record GetLeaderboardResult(
        UUID contestId,
        RoundSpan phase,
        RoundSpan currentSprint,
        RoundSpan currentQuarter,
        Integer effectiveToRound,
        List<LeaderboardEntry> rankings,
        List<RoundSpan> phases,
        LeaderboardEntry userEntry,
        boolean userInCurrentPage,
        int userPageOffset,
        int totalParticipants,
        boolean hasNext,
        boolean hasPrevious,
        int offset,
        int limit) {}
