package com.ligitabl.api.rest.leaderboard.getleaderboard;

public sealed interface GetLeaderboardError {
    record DefaultCompetitionNotFound(String message) implements GetLeaderboardError {
        public DefaultCompetitionNotFound() {
            this("Default Competition Not Found");
        }
    }

    record ActiveSeasonNotFound(String message) implements GetLeaderboardError {
        public ActiveSeasonNotFound() {
            this("Active Season Not Found");
        }
    }

    record PhasesNotConfigured() implements GetLeaderboardError {}

    record MainContestNotFound(String message) implements GetLeaderboardError {
        public MainContestNotFound() {
            this("Main Contest Not Found");
        }
    }

    record InvalidPhase(String phaseCode) implements GetLeaderboardError {}
}
