package com.ligitabl.api.usecases.prediction.makeswap;

import java.time.Instant;

import com.ligitabl.model.domain.SeasonPrediction;

public record SwapResult(
        boolean success, Instant nextSwapAt, double hoursUntilNext, SeasonPrediction updatedPrediction) {}
