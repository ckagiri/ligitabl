package com.ligitabl.api.rest.leaderboard.getuserdetail;

import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardError;

public sealed interface GetUserDetailError {

    record UserNotFound(String publicId) implements GetUserDetailError {}

    record NoFinalizedRounds() implements GetUserDetailError {}

    record CurrentRoundNotFound() implements GetUserDetailError {}

    record NoPredictionFound(String publicId, int round) implements GetUserDetailError {}

    record LeaderboardError(GetLeaderboardError cause) implements GetUserDetailError {}
}
