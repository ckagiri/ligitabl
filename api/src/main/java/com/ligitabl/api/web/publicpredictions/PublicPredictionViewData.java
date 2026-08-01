package com.ligitabl.api.web.publicpredictions;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ligitabl.api.web.shared.dto.PublicRankDto;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.SwapChange;

import lombok.Builder;

/**
 * Complete view data for the public, read-only prediction page — no access-mode/cooldown
 * concepts, since nobody can edit this view; just enough to render the table, round nav bounds,
 * and the "who am I looking at" banners.
 *
 * <p>{@code matches}/{@code pointsMap}/{@code goalDifferenceMap} are only populated for the
 * current/live round (never for a historical/scored round, where fixtures-in-progress and "current"
 * points/GD aren't meaningful) — they back the richer comparison-options view (points/GD/fixtures/
 * form toggles) on the public page.</p>
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
        Map<String, Integer> pointsMap,
        Map<String, Integer> goalDifferenceMap,
        List<SwapChange> roundSwaps) {
    public PublicPredictionViewData {
        Objects.requireNonNull(rows, "rows are required");
        rows = List.copyOf(rows);
        matches = matches != null ? Map.copyOf(matches) : Map.of();
        pointsMap = pointsMap != null ? Map.copyOf(pointsMap) : Map.of();
        goalDifferenceMap = goalDifferenceMap != null ? Map.copyOf(goalDifferenceMap) : Map.of();
        roundSwaps = roundSwaps != null ? List.copyOf(roundSwaps) : List.of();
    }

    public boolean isCurrentRound() {
        return viewingRound == currentRound;
    }
}
