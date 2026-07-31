package com.ligitabl.api.web.predictions.userpredictions;

import java.util.List;

/**
 * How a user's saved what-if guesses for a now-scored round actually turned out, bucketed for the
 * historical-view recap. {@code played} is the number of guesses that could be graded (a guessed
 * match with no real result contributes nothing), so it equals wins + draws + losses.
 */
public record WhatIfRecap(int played, List<Line> all, List<Line> wins, List<Line> draws, List<Line> losses) {

    /**
     * One graded match: "ARS - CHE 2 - 1 X" — actual scoreline, the outcome the user guessed, and
     * how that guess graded ({@code WIN} / {@code DRAW} / {@code LOSS}). Both the code and the
     * shorter name are carried: the shareable full list uses codes (compact, travels well in chat),
     * the per-bucket lists use names (nothing to keep short, easier to read).
     */
    public record Line(
            String homeTeamCode,
            String awayTeamCode,
            String homeTeamShorterName,
            String awayTeamShorterName,
            String actualScore,
            String guessedOutcome,
            String grade) {}

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
