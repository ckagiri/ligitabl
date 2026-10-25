package com.ligitabl.api.usecase.prediction.roundopeningswap;

import com.ligitabl.model.domain.SeasonPrediction;

public record RoundOpeningSwapResult(boolean success, int swapsApplied, SeasonPrediction updatedPrediction) {}
