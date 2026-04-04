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
 * - Subsequent swaps: 24-hour cooldown</p>
 */
public record SwapCooldown(Instant lastSwapAt, boolean initialPredictionMade, boolean openingRoundAvailable) {
    private static final Duration COOLDOWN = Duration.ofHours(24);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

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
        return elapsed.compareTo(COOLDOWN) < 0;
    }

    /**
     * Get remaining cooldown duration.
     */
    public Duration getRemainingCooldown(Instant now) {
        if (!isOnCooldown(now)) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(lastSwapAt, now);
        return COOLDOWN.minus(elapsed);
    }

    /**
     * Get next swap time.
     */
    public Instant getNextSwapTime() {
        if (lastSwapAt == null) {
            return Instant.now();
        }
        return lastSwapAt.plus(COOLDOWN);
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
        if (lastSwapAt == null) {
            return "You can make your first swap without waiting 24 hours";
        }

        if (canSwap(now)) {
            return "You can make changes now!";
        }

        String timeDisplay = getRemainingTimeDisplay(now);
        return "Cooldown active. You've already submitted changes for this period. Next change in " + timeDisplay + ".";
    }
}
