package com.ligitabl.api.rest.leaderboard;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.RoundSpan;

public record GetLeaderboardResult(UUID contestId, RoundSpan phase, List<LeaderboardEntry> rankings) {}
