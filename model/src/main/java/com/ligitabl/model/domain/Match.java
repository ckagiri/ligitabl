package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
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

    @Builder.Default
    private boolean wasPostponed = false;

    @Builder.Default
    private boolean wasSuspended = false;

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

    // Transient fields populated by repository
    private Team homeTeam;

    private Team awayTeam;

    /**
     * Gets home team. Throws if not loaded.
     */
    public Team getHomeTeam() {
        if (homeTeam == null) {
            throw new IllegalStateException(
                    "Home team not loaded for match " + id +
                            ". Use repository method that loads teams."
            );
        }
        return homeTeam;
    }

    /**
     * Gets away team. Throws if not loaded.
     */
    public Team getAwayTeam() {
        if (awayTeam == null) {
            throw new IllegalStateException(
                    "Away team not loaded for match " + id +
                            ". Use repository method that loads teams."
            );
        }
        return awayTeam;
    }

    /**
     * Checks if teams are loaded.
     */
    public boolean hasTeamsLoaded() {
        return homeTeam != null && awayTeam != null;
    }

    /**
     * Sets teams (called by repository).
     */
    public void setTeams(Team home, Team away) {
        this.homeTeam = home;
        this.awayTeam = away;
    }
}
