package com.ligitabl.api.usecase.prediction.getpublicprediction;

import java.util.UUID;

public sealed interface GetPublicPredictionError {
    record SeasonNotFound(UUID seasonId) implements GetPublicPredictionError {}

    record CurrentRoundNotFound(UUID seasonId) implements GetPublicPredictionError {}
}
