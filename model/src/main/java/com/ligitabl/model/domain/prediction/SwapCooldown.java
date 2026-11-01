package com.ligitabl.model.domain.prediction;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Swap cooldown state: unlimited changes before the initial prediction, the first swap after it
 * free, then {@link SeasonPrediction#SWAP_COOLDOWN} between swaps.
 */
public record SwapCooldown(Instant lastSwapAt, boolean initialPredictionMade, boolean openingRoundAvailable) {

    /**
     * Create initial cooldown state (no prediction made yet).
     */
    public static SwapCooldown initial() {
        return new SwapCooldown(null, false, false);
    }

    public boolean canSwap(Instant now) {
        Objects.requireNonNull(now, "now is required");

        // lastSwapAt == null means no real swap submitted yet — first real swap is free
        if (lastSwapAt == null) {
            return true;
        }

        // Check if cooldown has elapsed
        return !isOnCooldown(now);
    }

    /**
     * Check if currently on cooldown.
     */
    public boolean isOnCooldown(Instant now) {
        if (lastSwapAt == null) {
            return false;
        }
        Duration elapsed = Duration.between(lastSwapAt, now);
        return elapsed.compareTo(SeasonPrediction.SWAP_COOLDOWN) < 0;
    }

    public Duration getRemainingCooldown(Instant now) {
        if (!isOnCooldown(now)) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(lastSwapAt, now);
        return SeasonPrediction.SWAP_COOLDOWN.minus(elapsed);
    }
}
