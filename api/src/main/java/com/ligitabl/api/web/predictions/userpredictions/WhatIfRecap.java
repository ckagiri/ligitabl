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
