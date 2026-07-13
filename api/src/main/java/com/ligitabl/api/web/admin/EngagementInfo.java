package com.ligitabl.api.web.admin;

/**
 * Engagement signal for the admin users list, rendered as "total:current:swaps" (e.g. {@code
 * 2:1:0}) — {@code totalPredictions} is the all-time count of season predictions across every
 * season; {@code hasCurrentSeasonPrediction} is whether a row exists for the default competition's
 * current active season; {@code swaps} is the swap count within that current-season row (0 if none
 * exists). Splitting out {@code hasCurrentSeasonPrediction} from {@code swaps} distinguishes "never
 * touched this season" from "entered this season but hasn't swapped yet".
 *
 * <p>Delete-eligibility requires BOTH {@code pastSeasonPredictions() == 0} AND {@code swaps == 0} —
 * any current-season swap activity blocks deletion, full stop, regardless of past-season history.
 */
public record EngagementInfo(int totalPredictions, boolean hasCurrentSeasonPrediction, int swaps) {

    public int pastSeasonPredictions() {
        return totalPredictions - (hasCurrentSeasonPrediction ? 1 : 0);
    }

    public boolean eligibleForDelete() {
        return pastSeasonPredictions() == 0 && swaps == 0;
    }

    /**
     * 🚩 = never predicted anything, ever ({@code totalPredictions == 0}). ⚠️ = zero swaps in every
     * other case — this covers both a first-season user who started a current-season prediction but
     * hasn't swapped yet ({@code 1:1:0} — still eligible, just shown as "watching" rather than
     * flagged, since they did technically start something) and a returning user who's simply idle
     * this season ({@code 2:1:0} — not eligible). Anything with {@code swaps >= 1} is active and
     * never eligible, so it gets no icon at all.
     */
    public String indicator() {
        if (totalPredictions == 0) {
            return "🚩";
        }
        if (swaps == 0) {
            return "⚠️";
        }
        return "";
    }
}
