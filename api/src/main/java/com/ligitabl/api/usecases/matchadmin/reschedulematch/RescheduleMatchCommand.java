package com.ligitabl.api.usecases.matchadmin.reschedulematch;

import java.util.Optional;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RescheduleMatchCommand {

    @Nullable
    String competitionIdentifier;

    @Nullable
    Integer roundPosition; // null => current round
    String matchSlug;

    int newRoundPosition;
    String reason;

    public Optional<String> getCompetitionIdentifier() {
        return Optional.ofNullable(competitionIdentifier);
    }
}
