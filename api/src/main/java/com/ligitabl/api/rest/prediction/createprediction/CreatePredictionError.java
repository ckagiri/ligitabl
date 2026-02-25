package com.ligitabl.api.rest.prediction.createprediction;

import java.util.UUID;

public sealed interface CreatePredictionError {
    record NotFound() implements CreatePredictionError {}

    record Completed() implements CreatePredictionError {}

    record AlreadyJoined(UUID existingPredictionId) implements CreatePredictionError {}

    record SameTeam() implements CreatePredictionError {}

    record InvalidTeamCode(String code) implements CreatePredictionError {}

    record Ended(int currentRound, int maxRounds) implements CreatePredictionError {}

    record CurrentRoundNotFound(UUID seasonId) implements CreatePredictionError {}

    record MainContestNotFound() implements CreatePredictionError {}

    record TransactionFailed(String reason) implements CreatePredictionError {}
}
