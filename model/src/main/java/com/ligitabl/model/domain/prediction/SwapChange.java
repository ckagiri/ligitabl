package com.ligitabl.model.domain.prediction;

import java.time.Instant;
import java.util.List;

import com.ligitabl.model.domain.league.TeamRank;

public record SwapChange(
        Instant timestamp,
        String teamA, // Format: "ARS:1→4"
        String teamB // Format: "CHE:4→1"
        ) {

    /** Exchanges the two teams' positions in {@code rankings}, in place, and records the change. */
    public static SwapChange exchange(List<TeamRank> rankings, TeamRank teamA, TeamRank teamB, Instant at) {
        int indexA = rankings.indexOf(teamA);
        int indexB = rankings.indexOf(teamB);

        TeamRank newTeamA = teamA.withPosition(teamB.getPosition());
        TeamRank newTeamB = teamB.withPosition(teamA.getPosition());

        rankings.set(indexA, newTeamA);
        rankings.set(indexB, newTeamB);

        return new SwapChange(
                at,
                String.format("%s:%d→%d", teamA.getCode(), teamA.getPosition(), newTeamA.getPosition()),
                String.format("%s:%d→%d", teamB.getCode(), teamB.getPosition(), newTeamB.getPosition()));
    }
}
