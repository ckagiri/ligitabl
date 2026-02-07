package com.ligitabl.model.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Value object representing swap cooldown state.
 *
 * <p>Encapsulates the business logic for swap cooldown:
 * - Initial prediction mode: unlimited changes
 * - First swap after submission: free (no wait)
 * - Subsequent swaps: 24-hour cooldown (or 2 minutes in demo mode)</p>
 */
public record SwapCooldown(Instant lastSwapAt, boolean initialPredictionMade, int swapCount, boolean demoMode) {
    private static final Duration PRODUCTION_COOLDOWN = Duration.ofHours(24);
    private static final Duration DEV_COOLDOWN = Duration.ofMinutes(2);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    public SwapCooldown {
        // lastSwapAt can be null (never swapped)
        if (swapCount < 0) {
            throw new IllegalArgumentException("swapCount cannot be negative");
        }
    }

    /**
     * Create initial cooldown state (no prediction made yet).
     */
    public static SwapCooldown initial() {
        return new SwapCooldown(null, false, 0, true); // Demo mode by default
    }

    /**
     * Create cooldown state after a swap.
     */
    public static SwapCooldown afterSwap(Instant swapTime, int newSwapCount, boolean demoMode) {
        return new SwapCooldown(swapTime, true, newSwapCount, demoMode);
    }

    /**
     * Get the cooldown duration based on mode.
     */
    public Duration getCooldownDuration() {
        return demoMode ? DEV_COOLDOWN : PRODUCTION_COOLDOWN;
    }

    /**
     * Check if the user can swap now.
     */
    public boolean canSwap(Instant now) {
        Objects.requireNonNull(now, "now is required");

        // Initial prediction mode - unlimited changes
        if (!initialPredictionMade) {
            return true;
        }

        // First swap exception - one free swap after initial prediction
        if (swapCount == 0) {
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
        return elapsed.compareTo(getCooldownDuration()) < 0;
    }

    /**
     * Get remaining cooldown duration.
     */
    public Duration getRemainingCooldown(Instant now) {
        if (!isOnCooldown(now)) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(lastSwapAt, now);
        return getCooldownDuration().minus(elapsed);
    }

    /**
     * Get next swap time.
     */
    public Instant getNextSwapTime() {
        if (lastSwapAt == null) {
            return Instant.now();
        }
        return lastSwapAt.plus(getCooldownDuration());
    }

    /**
     * Get formatted last swap time.
     */
    public String getLastSwapAtFormatted() {
        if (lastSwapAt == null) {
            return "Never";
        }
        return FORMATTER.format(lastSwapAt);
    }

    /**
     * Get formatted next swap time.
     */
    public String getNextSwapAtFormatted(Instant now) {
        if (canSwap(now)) {
            return "Now";
        }
        return FORMATTER.format(getNextSwapTime());
    }

    /**
     * Get remaining time as a human-readable string.
     */
    public String getRemainingTimeDisplay(Instant now) {
        Duration remaining = getRemainingCooldown(now);
        long minutes = remaining.toMinutes();
        long hours = minutes / 60;

        if (hours >= 2) {
            return hours + "h";
        } else if (hours == 1) {
            long mins = minutes - 60;
            return mins > 0 ? "1h " + mins + "m" : "1h";
        } else {
            return minutes <= 1 ? "1m" : minutes + "m";
        }
    }

    /**
     * Get status message based on current state.
     */
    public String getStatusMessage(Instant now) {
        if (!initialPredictionMade) {
            return "Make your initial prediction! You can make unlimited changes before submitting.";
        }

        if (swapCount == 0) {
            return "You can make your first swap without waiting 24 hours";
        }

        if (canSwap(now)) {
            return "You can make changes now!";
        }

        String timeDisplay = getRemainingTimeDisplay(now);
        if (swapCount == 1) {
            return "Cooldown active. You've submitted changes for this period. Next change in " + timeDisplay + ".";
        }
        return "Cooldown active. You've already submitted changes for this period. Next change in " + timeDisplay + ".";
    }
}
