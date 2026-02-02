package com.ligitabl.api.rest.standings;

import jakarta.annotation.Nullable;
import lombok.Value;

@Value
public class GetDefaultRoundStandingsQuery {

    @Nullable
    Integer roundPosition;

    @Nullable
    String competitionSlug;

    public static GetDefaultRoundStandingsQuery currentRound(@Nullable String competition) {
        return new GetDefaultRoundStandingsQuery(null, competition);
    }

    public static GetDefaultRoundStandingsQuery byPosition(Integer position, @Nullable String competition) {
        return new GetDefaultRoundStandingsQuery(position, competition);
    }

    public boolean isCurrentRound() {
        return roundPosition == null;
    }
}
