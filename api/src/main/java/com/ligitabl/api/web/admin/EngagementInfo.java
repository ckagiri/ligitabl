package com.ligitabl.api.web.admin;

/**
 * <p>Delete-eligibility requires BOTH {@code pastSeasonPredictions() == 0} AND {@code
 * currentSeasonSwaps == 0} — any current-season swap activity blocks deletion, full stop,
 * regardless of past-season history or total lifetime swap count.
 */
public record EngagementInfo(
        int totalPredictions, boolean hasCurrentSeasonPrediction, int totalSwaps, int currentSeasonSwaps) {

    public int pastSeasonPredictions() {
        return totalPredictions - (hasCurrentSeasonPrediction ? 1 : 0);
    }

    public boolean eligibleForDelete() {
        return pastSeasonPredictions() == 0 && currentSeasonSwaps == 0;
    }

    /**
     * 🚩 = never predicted anything, ever ({@code totalPredictions == 0}). 👀 = zero swaps this
     * season in every other case — this covers both a first-season user who started a
     * current-season prediction but hasn't swapped yet ({@code 1:1} predictions — still eligible,
     * just shown as "watching" rather than flagged, since they did technically start something) and
     * a returning user who's simply idle this season. Anything with {@code currentSeasonSwaps >= 1}
     * is active and never eligible, so it gets no icon at all.
     */
    public String indicator() {
        if (totalPredictions == 0) {
            return "🚩";
        }
        if (currentSeasonSwaps == 0) {
            return "👀";
        }
        return "";
    }
}
