package com.ligitabl.api.usecases.matchadmin.transitionmatchstatus;

import java.util.Optional;

import com.ligitabl.model.domain.MatchStatus;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TransitionMatchCommand {

    @Nullable
    String competitionIdentifier;

    @Nullable
    Integer roundPosition; // null => current round

    String matchSlug;

    MatchStatus newStatus;
    String reason;

    @Nullable
    ScoreDto score;

    public Optional<String> getCompetitionIdentifier() {
        return Optional.ofNullable(competitionIdentifier);
    }

    @Value
    public static class ScoreDto {
        int homeGoals;
        int awayGoals;
    }
}
