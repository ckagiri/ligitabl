package com.ligitabl.model.domain;

import java.time.Instant;
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

    @NotNull
    private List<TeamRank> initialRankings;

    @NotNull
    private List<TeamRank> currentRankings;

    @Builder.Default
    private List<RoundSwap> swaps = new ArrayList<>();

    private Instant lastSwapAt;

    private int atRoundNumber;

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
        int swapCount = swaps == null
                ? 0
                : swaps.stream()
                        .mapToInt(roundSwap -> roundSwap.getChanges() == null
                                ? 0
                                : roundSwap.getChanges().size())
                        .sum();
        return new SwapCooldown(lastSwapAt, true, swapCount, false);
    }
}
