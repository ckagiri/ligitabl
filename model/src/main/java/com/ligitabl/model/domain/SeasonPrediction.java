package com.ligitabl.model.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
public class SeasonPrediction extends AbstractModel<UUID> {
    @NotNull
    private UUID userId;

    @NotNull
    private UUID seasonId;

    /**
     * Non-null only when this user pre-registered during the off-season window: the rankings
     * snapshot (after their one-time 0-5 swaps) at the moment of pre-registration. Null means
     * this prediction was created through the normal in-season join flow.
     */
    private List<TeamRank> initialRankings;

    @NotNull
    private List<TeamRank> currentRankings;

    @Builder.Default
    private List<RoundSwap> swaps = new ArrayList<>();

    private Instant lastSwapAt;

    private int openingCommittedRound;

    private int atRoundNumber;

    // Populated by the database (defaults/triggers)
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;

    /**
     * Adds a swap to the history for a specific round.
     */
    public void addSwap(int roundNumber, SwapChange change) {
        // Find or create RoundSwap for this round
        RoundSwap roundSwap = swaps.stream()
                .filter(rs -> rs.getRound() == roundNumber)
                .findFirst()
                .orElseGet(() -> {
                    RoundSwap newRoundSwap = new RoundSwap(roundNumber, new ArrayList<>());
                    swaps.add(newRoundSwap);
                    return newRoundSwap;
                });

        // Add the change
        roundSwap.getChanges().add(change);
    }

    public SwapCooldown getSwapCooldown() {
        return new SwapCooldown(lastSwapAt, true, false);
    }
}
