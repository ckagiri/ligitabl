package com.ligitabl.api.rest.leaderboard;

public sealed interface GetUserDetailError {

    record UserNotFound(String publicId) implements GetUserDetailError {}

    record NoFinalizedRounds() implements GetUserDetailError {}

    record NoPredictionFound(String publicId, int round) implements GetUserDetailError {}

    record LeaderboardError(GetLeaderboardError cause) implements GetUserDetailError {}
}
