package com.ligitabl.api.web.predictions.userpredictions;

import java.util.List;

public record WhatIfRecap(int played, List<Line> all, List<Line> wins, List<Line> draws, List<Line> losses) {

    public record Line(
            String homeTeamCode,
            String awayTeamCode,
            String homeTeamShorterName,
            String awayTeamShorterName,
            String actualScore,
            String guessedOutcome,
            String grade,
            /** Whether the guess named the exact scoreline, not just the right outcome. A stronger
             * WIN rather than a grade of its own, so the buckets and counts are unaffected — it
             * only changes how the result is marked. */
            boolean exact) {}

    public int winCount() {
        return wins.size();
    }

    public int drawCount() {
        return draws.size();
    }

    public int lossCount() {
        return losses.size();
    }
}
