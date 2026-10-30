package com.ligitabl.api.usecase.prediction.getprediction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ligitabl.api.usecase.prediction.shared.RankingSource;
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

    public record SwapStatus(boolean canSwap, String blockedReason, Instant nextSwapAt, Double hoursUntilNext) {

        public static SwapStatus allowed() {
            return new SwapStatus(true, null, null, null);
        }

        public static SwapStatus blocked(String reason) {
            return new SwapStatus(false, reason, null, null);
        }

        public static SwapStatus cooldown(Instant nextSwapAt, double hoursUntilNext) {
            return new SwapStatus(false, "COOLDOWN_ACTIVE", nextSwapAt, hoursUntilNext);
        }
    }
}
