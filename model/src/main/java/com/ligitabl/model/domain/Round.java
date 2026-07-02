package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Round extends AbstractModel<UUID> {
    @NotNull
    private UUID seasonId;

    @NotNull
    private String name;

    @NotNull
    private String slug;

    private int position;

    @NotNull
    @Builder.Default
    private boolean finalized = false;

    private OffsetDateTime advanceAt;

    @Builder.Default
    private boolean advanced = false;

    private OffsetDateTime advancedAt;

    // Populated by the database (defaults/triggers)
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;

    /**
     * Computes round status based on associated matches.
     * Called from repository after loading matches.
     */
    public RoundStatus computeStatus(List<Match> matches) {
        if (advanced) {
            return RoundStatus.ADVANCED;
        }

        if (finalized) {
            return RoundStatus.FINALIZED;
        }

        return computeMatchStatus(matches);
    }

    /**
     * Matches-only classification (OPEN/LOCKED/COMPLETED), independent of round lifecycle state
     * (advanced/finalized) — usable without a Round instance, e.g. when only a match list is
     * available.
     */
    public static RoundStatus computeMatchStatus(List<Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return RoundStatus.COMPLETED; // Empty round can finalize
        }

        boolean hasScheduled = false;
        boolean hasLive = false;
        boolean hasFinished = false;
        boolean hasSuspended = false;
        boolean hasCancelled = false;

        for (Match match : matches) {
            switch (match.getStatus()) {
                case SCHEDULED -> hasScheduled = true;
                case LIVE -> hasLive = true;
                case FINISHED -> hasFinished = true;
                case SUSPENDED -> hasSuspended = true;
                case CANCELLED -> hasCancelled = true;
                case POSTPONED -> {
                    // POSTPONED is neutral: doesn't block finalisation
                    // No flag needed
                }
            }
        }

        // COMPLETED: No Scheduled + No blocking statuses present
        // Allows: all FINISHED, all POSTPONED, mix of FINISHED+POSTPONED
        if (!hasScheduled && !hasLive && !hasSuspended && !hasCancelled) {
            return RoundStatus.COMPLETED;
        }

        // LOCKED: Any blocking status present (LIVE, SUSPENDED, CANCELLED)
        if (hasLive || hasSuspended || hasCancelled) {
            return RoundStatus.LOCKED;
        }

        // LOCKED: Round has started (some finished) but still has scheduled matches remaining.
        if (hasScheduled && hasFinished) {
            return RoundStatus.LOCKED;
        }

        // OPEN: Has scheduled matches, no blocking statuses
        return RoundStatus.OPEN;
    }
}
