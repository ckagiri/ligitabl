package com.ligitabl.api.web.publicpredictions;

import java.util.List;
import java.util.Objects;

import com.ligitabl.api.web.shared.dto.PublicRankDto;

import lombok.Builder;

/**
 * Complete view data for the public, read-only prediction page — no access-mode/cooldown
 * concepts, since nobody can edit this view; just enough to render the table, round nav bounds,
 * and the "who am I looking at" banners.
 */
@Builder
public record PublicPredictionViewData(
        List<PublicRankDto> rows,
        boolean userFound,
        boolean hasPrediction,
        String targetDisplayName,
        int currentRound,
        int lastRound,
        int viewingRound,
        int minRound,
        boolean seasonCompleted,
        boolean hasRoundResult,
        Integer totalScore,
        Integer totalHits,
        Integer zeroesCount) {
    public PublicPredictionViewData {
        Objects.requireNonNull(rows, "rows are required");
        rows = List.copyOf(rows);
    }

    public boolean isCurrentRound() {
        return viewingRound == currentRound;
    }
}
