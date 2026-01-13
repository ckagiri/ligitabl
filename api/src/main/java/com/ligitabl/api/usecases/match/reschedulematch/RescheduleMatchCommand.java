package com.ligitabl.api.usecases.match.reschedulematch;

import java.util.Optional;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RescheduleMatchCommand {

    @Nullable
    String competitionIdentifier;

    String roundPosition; // "current" or number
    String matchSlug;

    int newRoundPosition;
    String reason;

    public boolean isCurrentRound() {
        return "current".equalsIgnoreCase(roundPosition);
    }

    public Optional<Integer> getRoundPositionAsNumber() {
        if (isCurrentRound()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(roundPosition));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Optional<String> getCompetitionIdentifier() {
        return Optional.ofNullable(competitionIdentifier);
    }
}
