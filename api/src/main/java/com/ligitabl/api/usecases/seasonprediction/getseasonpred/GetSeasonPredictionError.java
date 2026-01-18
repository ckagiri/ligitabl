package com.ligitabl.api.usecases.seasonprediction.getseasonpred;

import java.util.UUID;

public sealed interface GetSeasonPredictionError {
    record SeasonNotFound() implements GetSeasonPredictionError {}

    record BaselineRankingsMissing(UUID seasonId) implements GetSeasonPredictionError {}

    record SeasonHasNoCurrentRound(UUID seasonId) implements GetSeasonPredictionError {}

    record CurrentRoundNotFound(UUID roundId) implements GetSeasonPredictionError {}
}
