package com.ligitabl.api.rest.prediction.createprediction;

import java.util.UUID;

public record CreatePredictionResult(UUID predictionId, UUID entryId, int atRoundNumber, String message) {}
