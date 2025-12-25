package com.ligitabl.model.domain;

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
    private RoundStatus status = RoundStatus.OPEN;

    /**
     * Computes round status based on associated matches.
     * Called from repository after loading matches.
     */
    public RoundStatus computeStatus(List<Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return RoundStatus.FINALISED; // Empty round can finalize
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
                    // POSTPONED is neutral: doesn’t block finalisation
                    // No flag needed
                }
            }
        }

        // FINALISED: No blocking statuses present
        // Allows: all FINISHED, all POSTPONED, mix of FINISHED+POSTPONED
        if (!hasScheduled && !hasLive && !hasSuspended && !hasCancelled) {
            return RoundStatus.FINALISED;
        }

        // LOCKED: Any blocking status present (LIVE, SUSPENDED, CANCELLED)
        if (hasLive || hasSuspended || hasCancelled) {
            return RoundStatus.LOCKED;
        }

        // OPEN: Has scheduled matches, no blocking statuses
        return RoundStatus.OPEN;
    }
}
