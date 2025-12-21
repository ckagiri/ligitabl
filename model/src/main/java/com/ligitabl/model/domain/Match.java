package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Match extends AbstractModel<UUID> {
    @NotNull
    private Integer clientId;

    @NotNull
    private UUID homeTeamId;

    @NotNull
    private UUID awayTeamId;

    @NotNull
    private UUID seasonId;

    @NotNull
    private UUID roundId;

    private Score score;

    @NotNull
    private String slug;

    @NotNull
    private MatchStatus status;

    private OffsetDateTime kickOff;

    private String venue;

    private int matchday;

    public boolean isPlayed() {
        return score != null;
    }

    public Optional<MatchResult> result() {
        if (!isPlayed()) return Optional.empty();
        return Optional.of(new MatchResult(score.getHomeGoals(), score.getAwayGoals()));
    }

    public Optional<TeamMatchView> viewFor(UUID teamId) {
        Objects.requireNonNull(teamId, "Team ID cannot be null");
        if (!isPlayed()) return Optional.empty();

        return result().flatMap(r -> {
            if (teamId.equals(homeTeamId)) {
                return Optional.of(new TeamMatchView(homeTeamId, awayTeamId, r.homeGoals(), r.awayGoals(), true));
            } else if (teamId.equals(awayTeamId)) {
                return Optional.of(new TeamMatchView(awayTeamId, homeTeamId, r.awayGoals(), r.homeGoals(), false));
            }
            return Optional.empty();
        });
    }

    @Override
    public String toString() {
        if (isPlayed()) {
            return String.format("Match[%s: %s %s %s]", id, homeTeamId, score, awayTeamId);
        } else {
            return String.format("Match[%s: %s vs %s - Not played]", id, homeTeamId, awayTeamId);
        }
    }
}
