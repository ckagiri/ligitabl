package com.ligitabl.model.domain.standings;

import com.ligitabl.model.domain.TeamRank;

import java.util.Objects;

public record StandingsTeamRank(TeamRank ranking, StandingsMetadata metadata) {
    public StandingsTeamRank {
        Objects.requireNonNull(ranking, "Ranking cannot be null");
        Objects.requireNonNull(metadata, "Metadata cannot be null");
    }

    public String teamCode() {
        return ranking.getCode();
    }

    public int position() {
        return ranking.getPosition();
    }

    @Override
    public String toString() {
        return String.format("%s: %s", ranking, metadata);
    }
}
