package com.ligitabl.api.web.predictions.userpredictions;

import java.util.List;

import lombok.Builder;

public record WhatIfRecap(int played, List<Line> all, List<Line> wins, List<Line> draws, List<Line> losses) {

    @Builder
    public record Line(
            String homeTeamCode,
            String awayTeamCode,
            String homeTeamShorterName,
            String awayTeamShorterName,
            String actualScore,
            String guessedOutcome,
            String grade,
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
