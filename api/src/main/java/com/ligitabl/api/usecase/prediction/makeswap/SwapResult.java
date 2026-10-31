package com.ligitabl.api.usecase.prediction.makeswap;

import java.time.Instant;

import com.ligitabl.model.domain.prediction.SeasonPrediction;

public record SwapResult(
        boolean success, Instant nextSwapAt, double hoursUntilNext, SeasonPrediction updatedPrediction) {}
