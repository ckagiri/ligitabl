package com.ligitabl.api.usecase.prediction.roundopeningswap;

import com.ligitabl.model.domain.prediction.SeasonPrediction;

public record RoundOpeningSwapResult(boolean success, int swapsApplied, SeasonPrediction updatedPrediction) {}
