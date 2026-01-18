package com.ligitabl.api.usecases.seasonprediction.createseasonpred;

import java.util.UUID;

public record CreateSeasonPredictionResult(UUID predictionId, UUID entryId, int atRoundNumber, String message) {}
