package com.ligitabl.api.usecases.leaderboard;

public sealed interface GetLeaderboardError {
    record DefaultCompetitionNotFound() implements GetLeaderboardError {}
    record ActiveSeasonNotFound() implements GetLeaderboardError {}
    record PhasesNotConfigured() implements GetLeaderboardError {}
    record MainContestNotFound() implements GetLeaderboardError {}
    record InvalidPhase(String phaseCode) implements GetLeaderboardError {}
}
