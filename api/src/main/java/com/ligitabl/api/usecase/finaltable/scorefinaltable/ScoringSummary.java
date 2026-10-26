package com.ligitabl.api.usecase.finaltable.scorefinaltable;

/**
 * @param scored rows written this pass
 * @param skipped rows already scored and left alone (only when {@code recompute} is false)
 * @param failed rows whose own scoring threw; logged and stepped over
 */
public record ScoringSummary(int scored, int skipped, int failed) {
    public int total() {
        return scored + skipped + failed;
    }
}
