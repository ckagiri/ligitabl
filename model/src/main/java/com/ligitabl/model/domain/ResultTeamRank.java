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
}
