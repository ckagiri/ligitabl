package com.ligitabl.api.rest.match.getdefaultroundmatches;

import java.util.Optional;

import jakarta.annotation.Nullable;
import lombok.Value;

@Value
public class GetDefaultRoundMatchesQuery {

    @Nullable
    Integer roundPosition;

    @Nullable
    String competitionIdentifier;

    public static GetDefaultRoundMatchesQuery currentRound() {
        return currentRound(null);
    }

    public static GetDefaultRoundMatchesQuery currentRound(@Nullable String competition) {
        return new GetDefaultRoundMatchesQuery(null, competition);
    }

    public static GetDefaultRoundMatchesQuery byPosition(Integer position, @Nullable String competition) {
        return new GetDefaultRoundMatchesQuery(position, competition);
    }

    public boolean isCurrentRound() {
        return roundPosition == null;
    }

    public Optional<String> getCompetitionIdentifier() {
        return Optional.ofNullable(competitionIdentifier);
    }
}
