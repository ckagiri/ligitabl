package com.ligitabl.api.usecases.seasonprediction.getseasonpred;

public enum RankingSource {
    USER_PREDICTION("Your Prediction"),
    ROUND_STANDINGS("Current Round Standings"),
    SEASON_BASELINE("Season Baseline");

    private final String displayName;

    RankingSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isUserPrediction() {
        return this == USER_PREDICTION;
    }

    public boolean isFallback() {
        return this != USER_PREDICTION;
    }
}
