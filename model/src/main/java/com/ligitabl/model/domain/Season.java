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

    /** On the outgoing season: has the pre-season window opened? */
    public boolean isPreSeasonOpen() {
        return preSeasonOpensAt != null && OffsetDateTime.now().isAfter(preSeasonOpensAt);
    }

    /**
     * On the upcoming (active) season: are predictions and swaps open for in-season play?
     * Null predictionsOpenAt defaults to open — covers seasons created before this field existed.
     */
    public boolean isPredictionsOpen() {
        return predictionsOpenAt == null || OffsetDateTime.now().isAfter(predictionsOpenAt);
    }

    /** On the active season (post-switch): true during the pre-season registration window. */
    public boolean isPreSeason() {
        return !isOffSeason() && !isPredictionsOpen() && isPreSeasonOpen();
    }

    /**
     * True once the season has finished but the pre-season window for the next season has not
     * (yet) opened. Reads preSeasonOpensAt (set on THIS/outgoing season), not predictionsOpenAt —
     * this season's own predictionsOpenAt is a different season's concern once completed.
     * A null preSeasonOpensAt still counts as off-season — a completed legacy season with no
     * pre-season config was never "pre-season open".
     *
     * <p>Note: it is valid (and expected, for backward compatibility with seasons lacking
     * preSeasonOpensAt) for isOffSeason() and isPredictionsOpen() to both be true simultaneously.
     * No mutual-exclusion guard is added between them by design.
     */
    public boolean isOffSeason() {
        return completed && (preSeasonOpensAt == null || OffsetDateTime.now().isBefore(preSeasonOpensAt));
    }
}
