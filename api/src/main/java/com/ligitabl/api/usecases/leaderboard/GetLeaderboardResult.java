package com.ligitabl.api.usecases.leaderboard;

import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.LeaderboardEntry;

import java.util.List;
import java.util.UUID;

public record GetLeaderboardResult(
        UUID contestId,
        RoundSpan phase,
        List<LeaderboardEntry> rankings
) {}
