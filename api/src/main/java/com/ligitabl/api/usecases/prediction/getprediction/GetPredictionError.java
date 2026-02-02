package com.ligitabl.api.usecases.prediction.getprediction;

import java.util.UUID;

public sealed interface GetPredictionError {
    record NotFound() implements GetPredictionError {}

    record BaselineRankingsMissing(UUID seasonId) implements GetPredictionError {}

    record HasNoCurrentRound(UUID seasonId) implements GetPredictionError {}

    record CurrentRoundNotFound(UUID roundId) implements GetPredictionError {}
}
