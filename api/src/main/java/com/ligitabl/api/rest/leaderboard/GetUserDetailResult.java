package com.ligitabl.api.rest.leaderboard;

import java.util.List;

public record GetUserDetailResult(
        String displayName,
        int position,
        int totalScore,
        int roundScore,
        int effectiveRound,
        boolean showingPreviousRound,
        List<PredictionTeam> currentPrediction) {

    public record PredictionTeam(String teamName) {}
}
