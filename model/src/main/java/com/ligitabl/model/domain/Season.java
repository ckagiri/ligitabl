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
     * Stored when entering setup mode so we can restore it on leave.
     */
    private UUID previousMainContestId;

    private UUID currentRoundId;

    private int currentMatchDay;

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

        this.previousMainContestId = this.mainContestId;
        this.mainContestId = null;
    }

    public void leaveSetupMode() {
        if (!isInSetupMode()) {
            throw new IllegalStateException("Season is not in setup mode");
        }

        if (previousMainContestId == null) {
            throw new IllegalStateException("Season cannot leave setup mode without a previous main contest");
        }

        this.mainContestId = previousMainContestId;
        this.previousMainContestId = null;
    }
}
