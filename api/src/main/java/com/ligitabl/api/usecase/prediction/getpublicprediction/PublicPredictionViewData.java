package com.ligitabl.api.usecase.prediction.getpublicprediction;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ligitabl.api.usecase.shared.dto.PublicRankDto;
import com.ligitabl.model.domain.league.Match;
import com.ligitabl.model.domain.prediction.SwapChange;

import lombok.Builder;

/**
 * View data for the public, read-only prediction page: the table, round nav bounds and the "who am
 * I looking at" banners.
 *
 * <p>{@code matches}, {@code standingsMap}, {@code pointsMap} and {@code goalDifferenceMap} are
 * populated only for the current round, where they back the points/GD/fixtures/form toggles.
 *
 * <p>{@code standingsMap} is the authoritative code&rarr;actual-position lookup and must not be
 * rebuilt from {@code rows}: a row's {@code actualPosition} falls back to the <em>predicted</em>
 * position when a team is missing from standings.
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
        Integer zeroesCount,
        Map<String, List<Match>> matches,
        Map<String, Integer> standingsMap,
        Map<String, Integer> pointsMap,
        Map<String, Integer> goalDifferenceMap,
        List<SwapChange> roundSwaps) {
    public PublicPredictionViewData {
        Objects.requireNonNull(rows, "rows are required");
        rows = List.copyOf(rows);
        matches = matches != null ? Map.copyOf(matches) : Map.of();
        standingsMap = standingsMap != null ? Map.copyOf(standingsMap) : Map.of();
        pointsMap = pointsMap != null ? Map.copyOf(pointsMap) : Map.of();
        goalDifferenceMap = goalDifferenceMap != null ? Map.copyOf(goalDifferenceMap) : Map.of();
        roundSwaps = roundSwaps != null ? List.copyOf(roundSwaps) : List.of();
    }

    public boolean isCurrentRound() {
        return viewingRound == currentRound;
    }
}
