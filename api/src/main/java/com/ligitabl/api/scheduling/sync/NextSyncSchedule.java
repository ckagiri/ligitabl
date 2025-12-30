package com.ligitabl.api.scheduling.sync;

import java.time.Duration;

/**
 * Next sync schedule based on match status
 */
public record NextSyncSchedule(
        Duration delay,
        String reason
) {
    public static NextSyncSchedule immediate(String reason) {
        return new NextSyncSchedule(Duration.ZERO, reason);
    }

    public static NextSyncSchedule minutes(long minutes, String reason) {
        return new NextSyncSchedule(Duration.ofMinutes(minutes), reason);
    }

    public static NextSyncSchedule hours(long hours, String reason) {
        return new NextSyncSchedule(Duration.ofHours(hours), reason);
    }
}
