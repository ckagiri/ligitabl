package com.ligitabl.api.usecases.seasonprediction.getseasonpred;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.RoundSwap;

public record GetSeasonPredictionResult(
        UUID predictionId,
        UUID seasonId,
        Integer atRoundNumber,
        int currentRoundNumber,
        String roundStatus,
        boolean seasonCompleted,
        RankingSource rankingSource,
        List<SeasonPredictionRankDto> rankings,
        List<RoundSwap> swaps,
        Instant lastSwapAt,
        SwapStatus swapStatus) {

    public record SwapStatus(boolean canSwap, String blockedReason, Instant nextSwapAt, Double hoursUntilNext) {}
}
