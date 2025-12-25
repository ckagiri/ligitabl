package com.ligitabl.api.usecases.prediction.makeswap;

import com.ligitabl.model.domain.SeasonPrediction;

import java.time.Instant;

public record SwapResult(
        boolean success,
        Instant nextSwapAt,
        double hoursUntilNext,
        SeasonPrediction updatedPrediction
) {}
