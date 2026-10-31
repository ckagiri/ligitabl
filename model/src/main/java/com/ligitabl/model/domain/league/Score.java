package com.ligitabl.model.domain.league;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Score {
    int homeGoals;
    int awayGoals;
}
