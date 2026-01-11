package com.ligitabl.api.usecases.standings.calc;

import jakarta.annotation.Nullable;
import lombok.Value;

@Value
public class CalculateRoundStandingsCommand {

    @Nullable
    Integer roundPosition;

    @Nullable
    String competitionSlug;

    public static CalculateRoundStandingsCommand currentRound(@Nullable String competition) {
        return new CalculateRoundStandingsCommand(null, competition);
    }

    public static CalculateRoundStandingsCommand byPosition(Integer position, @Nullable String competition) {
        return new CalculateRoundStandingsCommand(position, competition);
    }

    public boolean isCurrentRound() {
        return roundPosition == null;
    }
}
