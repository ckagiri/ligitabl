package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    public boolean wasPostponed() {
        return wasPostponed;
    }

    public void setScore(int homeGoals, int awayGoals) {
        this.score = Score.builder().homeGoals(homeGoals).awayGoals(awayGoals).build();
    }

    // Setup mode exists for retroactive data correction (wrong score, wrongly-FINISHED match,
    // marking something POSTPONED) — not for re-simulating match-day states like LIVE/SUSPENDED.
    private static final Set<MatchStatus> SETUP_MODE_TARGETS =
            Set.of(MatchStatus.SCHEDULED, MatchStatus.POSTPONED, MatchStatus.FINISHED);

    public static List<MatchStatus> validTransitionsFrom(MatchStatus status) {
        return validTransitionsFrom(status, false);
    }

    public static List<MatchStatus> validTransitionsFrom(MatchStatus status, boolean isSetupMode) {
        return Arrays.stream(MatchStatus.values())
                .filter(to -> to != status
                        && (isSetupMode ? SETUP_MODE_TARGETS.contains(to) : isAllowedTransition(status, to)))
                .toList();
    }

    public void transitionTo(MatchStatus newStatus, String reason) {
        transitionTo(newStatus, reason, false);
    }

    public void transitionTo(MatchStatus newStatus, String reason, boolean isSetupMode) {
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus must not be null");
        }

        MatchStatus from = this.status;
        if (from == null) {
            throw new IllegalStateException("Match has no current status");
        }
        if (from == newStatus) {
            return;
        }

        // In setup mode an admin may need to correct a mis-recorded status (e.g. a wrongly
        // FINISHED match) outside the normal match-day state machine, but only to the fixed
        // set of retroactive-correction targets — LIVE/SUSPENDED/CANCELLED still aren't reachable.
        boolean allowed = isSetupMode ? SETUP_MODE_TARGETS.contains(newStatus) : isAllowedTransition(from, newStatus);
        if (!allowed) {
            throw new IllegalStateException(String.format("Cannot transition from %s to %s", from, newStatus));
        }

        this.status = newStatus;
        if (newStatus == MatchStatus.POSTPONED) {
            this.wasPostponed = true;
        }
        if (newStatus == MatchStatus.SUSPENDED) {
            this.wasSuspended = true;
        }
    }

    public void rescheduleToRound(UUID newRoundId, boolean isSetupMode) {
        if (newRoundId == null) {
            throw new IllegalArgumentException("newRoundId must not be null");
        }

        this.roundId = newRoundId;

        if (!isSetupMode) {
            // Outside setup mode, rescheduling returns the match to SCHEDULED.
            this.status = MatchStatus.SCHEDULED;
        }
    }

    private static boolean isAllowedTransition(MatchStatus from, MatchStatus to) {
        return switch (from) {
            case SCHEDULED -> to == MatchStatus.SCHEDULED
                    || to == MatchStatus.LIVE
                    || to == MatchStatus.FINISHED
                    || to == MatchStatus.POSTPONED
                    || to == MatchStatus.CANCELLED;
            case LIVE -> to == MatchStatus.SUSPENDED || to == MatchStatus.FINISHED;
            case SUSPENDED -> to == MatchStatus.CANCELLED;
            case POSTPONED -> to == MatchStatus.SCHEDULED || to == MatchStatus.CANCELLED;
            case CANCELLED -> to == MatchStatus.SCHEDULED || to == MatchStatus.POSTPONED || to == MatchStatus.FINISHED;
            case FINISHED -> false;
        };
    }

    public boolean isPlayed() {
        return score != null;
    }

    public boolean isComplete() {
        return status == MatchStatus.FINISHED || status == MatchStatus.POSTPONED;
    }

    public boolean isBlocking() {
        return status == MatchStatus.CANCELLED || status == MatchStatus.SUSPENDED;
    }

    public void withDatabaseId(UUID id) {
        this.setId(id);
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

    // Populated by the database (defaults/triggers)
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;

    // Transient fields populated by repository
    private Team homeTeam;

    private Team awayTeam;

    /**
     * Gets home team. Throws if not loaded.
     */
    public Team getHomeTeam() {
        if (homeTeam == null) {
            throw new IllegalStateException(
                    "Home team not loaded for match " + id + ". Use repository method that loads teams.");
        }
        return homeTeam;
    }

    /**
     * Gets away team. Throws if not loaded.
     */
    public Team getAwayTeam() {
        if (awayTeam == null) {
            throw new IllegalStateException(
                    "Away team not loaded for match " + id + ". Use repository method that loads teams.");
        }
        return awayTeam;
    }

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
