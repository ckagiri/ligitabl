package com.ligitabl.model.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Season extends AbstractModel<UUID> {
    @NotNull
    private Integer clientId;

    @NotNull
    private UUID competitionId;

    @NotNull
    private String name;

    @NotNull
    private SeasonSlug slug;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private int maxRounds;

    private boolean completed;

    private OffsetDateTime completedAt;

    private int totalTeams;

    private int maxHitPoints;

    private List<TeamRank> initialRankings;

    private UUID mainContestId;

    /**
     * When entering setup mode, we detach the current main contest so we can reattach it when leaving.
     *
     * Invariant: exactly one of (mainContestId, detachedContestId) should be non-null.
     */
    private UUID detachedContestId;

    private UUID currentRoundId;

    private int currentMatchDay;

    /** Set on the outgoing season: when to auto-switch activeSeasonId to the upcoming season. */
    private OffsetDateTime preSeasonOpensAt;

    /** Set on the upcoming season: when predictions and swaps open for in-season play. */
    private OffsetDateTime predictionsOpenAt;

    /**
     * Setup mode is represented by the absence of a main contest.
     */
    public boolean isInSetupMode() {
        return mainContestId == null;
    }

    public void enterSetupMode() {
        if (isInSetupMode()) {
            throw new IllegalStateException("Season is already in setup mode");
        }

        this.detachedContestId = this.mainContestId;
        this.mainContestId = null;
    }

    public void leaveSetupMode() {
        if (!isInSetupMode()) {
            throw new IllegalStateException("Season is not in setup mode");
        }

        if (detachedContestId == null) {
            throw new IllegalStateException("Season cannot leave setup mode without a detached contest");
        }

        this.mainContestId = detachedContestId;
        this.detachedContestId = null;
    }

    public boolean isPreSeasonOpen() {
        return preSeasonOpensAt != null && OffsetDateTime.now().isAfter(preSeasonOpensAt);
    }

    public SeasonState getSeasonState() {
        boolean pastActualEnd = completed && endDate != null && LocalDate.now().isAfter(endDate);
        boolean beforeActualStart =
                !completed && startDate != null && LocalDate.now().isBefore(startDate);
        boolean preSeasonOpen = preSeasonOpensAt != null && OffsetDateTime.now().isAfter(preSeasonOpensAt);
        boolean predictionsOpen =
                predictionsOpenAt == null || OffsetDateTime.now().isAfter(predictionsOpenAt);

        if ((!preSeasonOpen && pastActualEnd) || (!predictionsOpen && !preSeasonOpen && beforeActualStart)) {
            return SeasonState.OFF_SEASON;
        }
        if (!completed && predictionsOpen) {
            return SeasonState.IN_PLAY;
        }
        if (completed && preSeasonOpen && predictionsOpen) {
            return SeasonState.INACTIVE;
        }
        if (preSeasonOpen && beforeActualStart) {
            return SeasonState.PRE_SEASON;
        }
        return SeasonState.INACTIVE;
    }

    public boolean isOffSeason() {
        return getSeasonState() == SeasonState.OFF_SEASON;
    }

    public boolean isInPlay() {
        return getSeasonState() == SeasonState.IN_PLAY;
    }

    public boolean isPreSeason() {
        return getSeasonState() == SeasonState.PRE_SEASON;
    }

    /**
     * True when none of the other three phases apply — e.g. a completed season whose
     * preSeasonOpensAt has passed but whose successor hasn't been promoted/configured yet.
     */
    public boolean isInactive() {
        return getSeasonState() == SeasonState.INACTIVE;
    }
}
