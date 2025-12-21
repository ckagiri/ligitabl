package com.ligitabl.model.domain.standings.stats;

import java.util.Objects;
import java.util.UUID;

public record StandingDto(
        int position,
        UUID teamId,
        String teamName,
        int played,
        int won,
        int drawn,
        int lost,
        int goalsFor,
        int goalsAgainst,
        int goalDifference,
        int points,
        HomeAwayStats homeStats,
        HomeAwayStats awayStats) {

    public static StandingDto from(Standing standing, String teamName) {
        Objects.requireNonNull(standing);
        Objects.requireNonNull(teamName);

        TeamStats s = standing.stats();
        return new StandingDto(
                standing.position(),
                s.teamId(),
                teamName,
                s.played(),
                s.won(),
                s.drawn(),
                s.lost(),
                s.goalsFor(),
                s.goalsAgainst(),
                s.goalDiff(),
                s.points(),
                s.homeStats(),
                s.awayStats());
    }
}
