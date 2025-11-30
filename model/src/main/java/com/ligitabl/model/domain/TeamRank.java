package com.ligitabl.model.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeamRank {
    String code;
    int position;
}
