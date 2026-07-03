package com.ligitabl.api.web.predictions.userpredictions;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ligitabl.api.rest.prediction.shared.PredictionAccessMode;
import com.ligitabl.api.rest.prediction.shared.RankingSource;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.SwapCooldown;
import com.ligitabl.model.domain.TeamRank;

import lombok.Builder;

/**
 * Complete view data returned by this use case.
 *
 * <p>Contains all information needed by the template to render the prediction view,
 * including access mode for UI control rendering.</p>
 */
@Builder
public record UserPredictionViewData(
        List<TeamRank> rankings,
        RankingSource source,
        PredictionAccessMode accessMode,
        SwapCooldown swapCooldown,
        Map<String, List<Match>> matches,
        Map<String, Integer> standingsMap,
        Map<String, Integer> pointsMap,
        Map<String, Integer> goalDifferenceMap,
        int currentRound,
        int lastRound,
        int viewingRound,
        Integer atRoundNumber,
        boolean seasonCompleted,
        String roundState,
        RoundResult roundResult, // Present for historical views with scored results
        List<SwapChange> roundSwapHistory, // Swaps made in the viewed round; null for guests/other users
        Integer seasonBestScore, // Best single-GW score in the season up to viewingRound
        Integer sprintBestScore, // Best single-GW score in viewingRound's sprint up to viewingRound
        String sprintLabel, // e.g. "Sprint 5"
        boolean isGuest // Set from UserContext.isGuest() — not derivable from accessMode alone
        ) {
    public UserPredictionViewData {
        Objects.requireNonNull(rankings, "rankings are required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(accessMode, "accessMode is required");
        rankings = List.copyOf(rankings);
        matches = matches != null ? Map.copyOf(matches) : Map.of();
        standingsMap = standingsMap != null ? Map.copyOf(standingsMap) : Map.of();
        pointsMap = pointsMap != null ? Map.copyOf(pointsMap) : Map.of();
        goalDifferenceMap = goalDifferenceMap != null ? Map.copyOf(goalDifferenceMap) : Map.of();
        roundSwapHistory = roundSwapHistory != null ? List.copyOf(roundSwapHistory) : null;
        // seasonBestScore, sprintBestScore, sprintLabel are nullable — no defensive copy needed
    }

    /**
     * Check if this view has historical round result data.
     */
    public boolean hasRoundResult() {
        return roundResult != null;
    }

    /**
     * Check if viewing the current round.
     */
    public boolean isCurrentRound() {
        return viewingRound == currentRound;
    }

    /**
     * Check if user can swap teams.
     * Returns true for EDITABLE or CAN_CREATE_ENTRY modes.
     */
    public boolean canSwap() {
        return accessMode == PredictionAccessMode.EDITABLE || accessMode == PredictionAccessMode.CAN_CREATE_ENTRY;
    }

    /**
     * Check if user can create a new entry (initial prediction).
     */
    public boolean canCreateEntry() {
        return accessMode == PredictionAccessMode.CAN_CREATE_ENTRY;
    }

    /**
     * Check if the view is readonly.
     */
    public boolean isReadonly() {
        return accessMode.isReadonly();
    }

}
