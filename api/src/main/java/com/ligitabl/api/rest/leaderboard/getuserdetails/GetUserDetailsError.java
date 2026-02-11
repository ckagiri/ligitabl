package com.ligitabl.api.rest.leaderboard.getuserdetails;

import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardError;

public sealed interface GetUserDetailsError {

    record UserNotFound(String publicId) implements GetUserDetailsError {}

    record NoFinalizedRounds() implements GetUserDetailsError {}

    record CurrentRoundNotFound() implements GetUserDetailsError {}

    record NoPredictionFound(String publicId, int round) implements GetUserDetailsError {}

    record LeaderboardError(GetLeaderboardError cause) implements GetUserDetailsError {}
}
