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

    public static boolean isCurrentKeyword(String roundPosition) {
        return roundPosition != null && roundPosition.equalsIgnoreCase("current");
    }

    public static Integer parseRoundPosition(String roundPosition) {
        if (isCurrentKeyword(roundPosition)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(roundPosition);
            if (parsed < 1) {
                throw new IllegalArgumentException("Round position must be at least 1");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Round position must be 'current' or a number >= 1", ex);
        }
    }

    public boolean isCurrentRound() {
        return roundPosition == null;
    }

    public Optional<String> getCompetitionIdentifier() {
        return Optional.ofNullable(competitionIdentifier);
    }
}
