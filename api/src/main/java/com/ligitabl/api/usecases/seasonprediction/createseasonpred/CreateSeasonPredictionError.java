package com.ligitabl.api.usecases.seasonprediction.createseasonpred;

import java.util.List;
import java.util.UUID;

public sealed interface CreateSeasonPredictionError {
    record SeasonNotFound() implements CreateSeasonPredictionError {}

    record SeasonCompleted() implements CreateSeasonPredictionError {}

    record AlreadyJoined(UUID existingPredictionId) implements CreateSeasonPredictionError {}

    record InvalidTeamCount(int provided, int required) implements CreateSeasonPredictionError {}

    record DuplicatePositions(List<Integer> duplicates) implements CreateSeasonPredictionError {}

    record DuplicateTeamCodes(List<String> duplicates) implements CreateSeasonPredictionError {}

    record InvalidTeamCodes(List<String> invalidCodes) implements CreateSeasonPredictionError {}

    record SeasonEnded(int currentRound, int maxRounds) implements CreateSeasonPredictionError {}

    record DefaultContestNotFound() implements CreateSeasonPredictionError {}

    record TransactionFailed(String reason) implements CreateSeasonPredictionError {}
}
