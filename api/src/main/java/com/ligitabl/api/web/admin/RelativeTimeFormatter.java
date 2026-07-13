package com.ligitabl.api.web.admin;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.text.NumberFormat;
import java.util.Locale;

public final class RelativeTimeFormatter {

    private RelativeTimeFormatter() {}

    public static String format(OffsetDateTime at, OffsetDateTime now) {
        if (at == null) {
            return "Never";
        }

        Duration elapsed = Duration.between(at, now);
        if (elapsed.isNegative()) {
            elapsed = Duration.ZERO;
        }

        long minutes = elapsed.toMinutes();
        long hours = elapsed.toHours();
        long days = elapsed.toDays();

        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        return NumberFormat.getIntegerInstance(Locale.US).format(days) + (days == 1 ? " day ago" : " days ago");
    }
}
