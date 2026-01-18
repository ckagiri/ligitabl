package com.ligitabl.api.usecases.seasonprediction.makeswap;

import java.time.Instant;

import com.ligitabl.model.domain.SeasonPrediction;

public record SwapResult(
        boolean success, Instant nextSwapAt, double hoursUntilNext, SeasonPrediction updatedPrediction) {}
