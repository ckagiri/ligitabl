package com.ligitabl.api.usecases.prediction.createprediction;

import java.util.List;
import java.util.UUID;

public sealed interface CreatePredictionError {
    record NotFound() implements CreatePredictionError {}

    record Completed() implements CreatePredictionError {}

    record AlreadyJoined(UUID existingPredictionId) implements CreatePredictionError {}

    record InvalidTeamCount(int provided, int required) implements CreatePredictionError {}

    record DuplicatePositions(List<Integer> duplicates) implements CreatePredictionError {}

    record DuplicateTeamCodes(List<String> duplicates) implements CreatePredictionError {}

    record InvalidTeamCodes(List<String> invalidCodes) implements CreatePredictionError {}

    record Ended(int currentRound, int maxRounds) implements CreatePredictionError {}

    record DefaultContestNotFound() implements CreatePredictionError {}

    record TransactionFailed(String reason) implements CreatePredictionError {}
}
