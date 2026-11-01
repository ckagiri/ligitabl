package com.ligitabl.api.web.admin;

/** Delete-eligible only when {@code pastSeasonPredictions() == 0} and {@code currentSeasonSwaps == 0}. */
public record EngagementInfo(
        int totalPredictions, boolean hasCurrentSeasonPrediction, int totalSwaps, int currentSeasonSwaps) {

    public int pastSeasonPredictions() {
        return totalPredictions - (hasCurrentSeasonPrediction ? 1 : 0);
    }

    public boolean eligibleForDelete() {
        return pastSeasonPredictions() == 0 && currentSeasonSwaps == 0;
    }

    /**
     * 🚩 = never predicted anything. 👀 = no swaps this season otherwise, whether a first-season user
     * who hasn't swapped yet (still eligible) or an idle returning user. Any current-season swap
     * means active and never eligible, so no icon.
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
