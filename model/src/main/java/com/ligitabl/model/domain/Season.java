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

    /**
     * On the outgoing season: has the pre-season window opened? Standalone raw date check — used
     * directly by SeasonActivationService/admin visibility against the outgoing (still-in-play)
     * season, so it deliberately does NOT factor in isInPlay()/getSeasonState().
     */
    public boolean isPreSeasonOpen() {
        return preSeasonOpensAt != null && OffsetDateTime.now().isAfter(preSeasonOpensAt);
    }

    /**
     * Coarse-grained season phase, in priority order: OFF_SEASON, then IN_PLAY, then PRE_SEASON,
     * with INACTIVE as the fallback when none of the three apply. Single source of truth for
     * phase precedence — isOffSeason()/isInPlay()/isPreSeason()/isInactive() all just compare
     * against this.
     *
     * <p>OFF_SEASON and PRE_SEASON both additionally require being outside the season's own
     * start/end window (past endDate for a completed season, or before startDate for an upcoming
     * one) — guards against reporting either phase purely off the
     * completed/predictionsOpenAt/preSeasonOpensAt flags if they're out of sync with the season's
     * actual dates. For a not-yet-completed season, this only reclassifies what would otherwise
     * fall into INACTIVE (predictions not yet open AND pre-season not yet open) as OFF_SEASON —
     * it never preempts a genuine IN_PLAY reading, since it's gated on predictionsOpenAt NOT being
     * open, checked before the beforeActualStart branch is considered.
     *
     * <p>A completed season whose preSeasonOpensAt and predictionsOpenAt have both already passed
     * is explicitly INACTIVE rather than PRE_SEASON — once predictions are open, staying
     * "completed" is a data inconsistency (the season should have been promoted/reverted), not a
     * genuine pre-season window.
     */
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
        if (preSeasonOpen && (pastActualEnd || beforeActualStart)) {
            return SeasonState.PRE_SEASON;
        }
        return SeasonState.INACTIVE;
    }

    /**
     * True once the season has finished but the pre-season window for the next season has not
     * (yet) opened. A null preSeasonOpensAt still counts as off-season — a completed legacy
     * season with no pre-season config was never "pre-season open".
     */
    public boolean isOffSeason() {
        return getSeasonState() == SeasonState.OFF_SEASON;
    }

    /**
     * On the upcoming (active) season: are predictions and swaps open for in-season play?
     * Null predictionsOpenAt defaults to open — covers seasons created before this field existed.
     * A completed season is never "in play" — this makes isOffSeason() and isInPlay() mutually
     * exclusive.
     */
    public boolean isInPlay() {
        return getSeasonState() == SeasonState.IN_PLAY;
    }

    /** On the active season (post-switch): true during the pre-season registration window. */
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
