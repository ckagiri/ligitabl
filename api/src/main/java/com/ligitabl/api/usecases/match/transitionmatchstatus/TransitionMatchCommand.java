package com.ligitabl.api.usecases.match.transitionmatchstatus;

import java.util.Optional;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

import com.ligitabl.model.domain.MatchStatus;

@Value
@Builder
public class TransitionMatchCommand {

    @Nullable
    String competitionIdentifier;

    String roundPosition; // "current" or number
    String matchSlug;

    MatchStatus newStatus;
    String reason;

    @Nullable
    ScoreDto score;

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

    @Value
    public static class ScoreDto {
        int homeGoals;
        int awayGoals;
    }
}
