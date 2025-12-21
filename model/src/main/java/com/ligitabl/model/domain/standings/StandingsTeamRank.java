package com.ligitabl.model.domain.standings;

import java.util.Objects;

import com.ligitabl.model.domain.TeamRank;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class StandingsTeamRank {
    TeamRank ranking;
    StandingsMetadata metadata;

    public StandingsTeamRank(TeamRank ranking, StandingsMetadata metadata) {
        this.ranking = Objects.requireNonNull(ranking, "Ranking cannot be null");
        this.metadata = Objects.requireNonNull(metadata, "Metadata cannot be null");
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
