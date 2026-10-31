package com.ligitabl.model.domain.prediction;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing swap cooldown state.
 *
 * <p>Encapsulates the business logic for swap cooldown:
 * - Initial prediction mode: unlimited changes
 * - First swap after submission: free (no wait)
 * - Subsequent swaps: {@link SeasonPrediction#SWAP_COOLDOWN}</p>
 */
public record SwapCooldown(Instant lastSwapAt, boolean initialPredictionMade, boolean openingRoundAvailable) {

    /**
     * Create initial cooldown state (no prediction made yet).
     */
    public static SwapCooldown initial() {
        return new SwapCooldown(null, false, false);
    }

    /**
     * Check if the user can swap now.
     */
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

    /**
     * Get remaining cooldown duration.
     */
    public Duration getRemainingCooldown(Instant now) {
        if (!isOnCooldown(now)) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(lastSwapAt, now);
        return SeasonPrediction.SWAP_COOLDOWN.minus(elapsed);
    }
}
