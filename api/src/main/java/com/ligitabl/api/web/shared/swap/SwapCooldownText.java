package com.ligitabl.api.web.shared.swap;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.ligitabl.model.domain.prediction.SwapCooldown;

/** The cooldown banner's wording. */
public final class SwapCooldownText {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private SwapCooldownText() {}

    public static String lastSwapAt(SwapCooldown cooldown) {
        return cooldown.lastSwapAt() == null ? "Never" : FORMATTER.format(cooldown.lastSwapAt());
    }

    public static String statusMessage(SwapCooldown cooldown, Instant now) {
        if (cooldown.lastSwapAt() == null) {
            return "You can make your first swap without waiting 24 hours";
        }
        if (cooldown.canSwap(now)) {
            return "You can make changes now!";
        }
        return "Cooldown active. You've already submitted changes for this period. Next change in "
                + remainingTime(cooldown.getRemainingCooldown(now)) + ".";
    }

    static String remainingTime(Duration remaining) {
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
}
