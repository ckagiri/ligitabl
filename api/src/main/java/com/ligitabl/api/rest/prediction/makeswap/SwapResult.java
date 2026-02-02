package com.ligitabl.api.rest.prediction.makeswap;

import java.time.Instant;

import com.ligitabl.model.domain.SeasonPrediction;

public record SwapResult(
        boolean success, Instant nextSwapAt, double hoursUntilNext, SeasonPrediction updatedPrediction) {}
