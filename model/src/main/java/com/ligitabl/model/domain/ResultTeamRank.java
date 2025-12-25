package com.ligitabl.model.domain;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ResultTeamRank {
    TeamRank ranking;
    int standingsPosition;
    int hit;

    public ResultTeamRank(TeamRank ranking, int standingsPosition, int hit) {
        this.ranking = ranking;
        this.standingsPosition = standingsPosition;
        this.hit = hit;
    }
}
