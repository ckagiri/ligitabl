package com.ligitabl.api.rest.prediction.getprediction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.RoundSwap;

public record GetPredictionResult(
        UUID predictionId,
        UUID seasonId,
        Integer atRoundNumber,
        int currentRoundNumber,
        String roundStatus,
        boolean seasonCompleted,
        RankingSource rankingSource,
        List<PredictionRankDto> rankings,
        List<RoundSwap> swaps,
        Instant lastSwapAt,
        SwapStatus swapStatus) {

    public record SwapStatus(boolean canSwap, String blockedReason, Instant nextSwapAt, Double hoursUntilNext) {}
}
