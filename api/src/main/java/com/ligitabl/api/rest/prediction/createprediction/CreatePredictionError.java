package com.ligitabl.api.rest.prediction.createprediction;

import java.util.UUID;

public sealed interface CreatePredictionError {
    record SeasonNotFound() implements CreatePredictionError {}

    record Completed() implements CreatePredictionError {}

    record SeasonInSetupMode() implements CreatePredictionError {}

    record AlreadyJoined(UUID existingPredictionId) implements CreatePredictionError {}

    record TooManySwaps(int provided, int max) implements CreatePredictionError {}

    record SameTeam() implements CreatePredictionError {}

    record InvalidTeamCode(String code) implements CreatePredictionError {}

    record Ended(int currentRound, int maxRounds) implements CreatePredictionError {}

    record CurrentRoundNotFound(UUID seasonId) implements CreatePredictionError {}

    record MainContestNotFound() implements CreatePredictionError {}

    /** A round-0 pre-season registration row was found with no initialRankings snapshot — data integrity issue. */
    record CorruptPreSeasonRegistration(UUID predictionId) implements CreatePredictionError {}

    record TransactionFailed(String reason) implements CreatePredictionError {}
}
