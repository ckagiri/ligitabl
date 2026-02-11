package com.ligitabl.api.rest.leaderboard.getuserdetails;

import java.util.List;

public record GetUserDetailsResult(
        String displayName,
        int position,
        int totalScore,
        int roundScore,
        int effectiveRound,
        boolean showingPreviousRound,
        List<PredictionTeam> currentPrediction) {

    public record PredictionTeam(String teamName) {}
}
