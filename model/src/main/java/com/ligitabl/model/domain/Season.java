package com.ligitabl.model.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
     * The zone the {@code startDate}/{@code endDate} comparisons in {@link #getSeasonState(Instant)}
     * are evaluated in.
     *
     * <p>These are {@link LocalDate} fields, so an {@link Instant} alone cannot be compared against
     * them — a calendar date has to be derived first, and that needs a zone. UTC is chosen to match
     * the application's {@code Clock} bean ({@code Clock.systemUTC()}), so a caller passing
     * {@code clock.instant()} gets the date the rest of the system agrees on.
     *
     * <p>This is a deliberate change from the wall-clock versions these methods replaced, which used
     * {@code LocalDate.now()} and therefore the JVM's default zone. On a UTC JVM the two are
     * identical; elsewhere they disagree for the part of the day where the local date and the UTC
     * date differ.
     */
    private static final ZoneOffset SEASON_DATE_ZONE = ZoneOffset.UTC;

    public boolean isPreSeasonOpen(Instant at) {
        return preSeasonOpensAt != null && at.isAfter(preSeasonOpensAt.toInstant());
    }

    public SeasonState getSeasonState(Instant at) {
        LocalDate today = LocalDate.ofInstant(at, SEASON_DATE_ZONE);
        boolean pastActualEnd = completed && endDate != null && today.isAfter(endDate);
        boolean beforeActualStart = !completed && startDate != null && today.isBefore(startDate);
        boolean preSeasonOpen = isPreSeasonOpen(at);
        boolean predictionsOpen = predictionsOpenAt == null || at.isAfter(predictionsOpenAt.toInstant());

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

    public boolean isOffSeason(Instant at) {
        return getSeasonState(at) == SeasonState.OFF_SEASON;
    }

    public boolean isInPlay(Instant at) {
        return getSeasonState(at) == SeasonState.IN_PLAY;
    }

    public boolean isPreSeason(Instant at) {
        return getSeasonState(at) == SeasonState.PRE_SEASON;
    }

    /**
     * True when none of the other three phases apply — e.g. a completed season whose
     * preSeasonOpensAt has passed but whose successor hasn't been promoted/configured yet.
     */
    public boolean isInactive(Instant at) {
        return getSeasonState(at) == SeasonState.INACTIVE;
    }
}
