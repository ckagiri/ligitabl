package com.ligitabl.api.scheduling.syncmatches;

import java.time.Duration;

/**
 * Next sync schedule based on match status
 *
 * @param shouldNotify Whether this schedule is worth an admin Slack notification unconditionally —
 *     no reason-text deduping — since {@code false} is already used for the high-frequency
 *     live/imminent/soon polling branches that would otherwise spam on every minute-by-minute
 *     reason change.
 * @param phase Coarse polling phase for schedules that repeat rapidly (LIVE/IMMINENT/SOON) and
 *     therefore don't set {@code shouldNotify}. Callers should still notify once when this phase
 *     differs from the previously observed one — i.e. on entry into the phase — then stay silent
 *     for repeats. {@link Phase#NONE} for every other schedule, which is already covered by
 *     {@code shouldNotify}.
 */
public record NextSyncSchedule(Duration delay, String reason, boolean shouldNotify, Phase phase) {

    public enum Phase {
        NONE,
        LIVE,
        IMMINENT,
        SOON
    }

    public NextSyncSchedule(Duration delay, String reason) {
        this(delay, reason, false, Phase.NONE);
    }

    public NextSyncSchedule(Duration delay, String reason, boolean shouldNotify) {
        this(delay, reason, shouldNotify, Phase.NONE);
    }

    public NextSyncSchedule withPhase(Phase phase) {
        return new NextSyncSchedule(delay, reason, shouldNotify, phase);
    }

    public static NextSyncSchedule immediate(String reason) {
        return immediate(reason, false);
    }

    public static NextSyncSchedule immediate(String reason, boolean shouldNotify) {
        return new NextSyncSchedule(Duration.ZERO, reason, shouldNotify);
    }

    public static NextSyncSchedule minutes(long minutes, String reason) {
        return minutes(minutes, reason, false);
    }

    public static NextSyncSchedule minutes(long minutes, String reason, boolean shouldNotify) {
        return new NextSyncSchedule(Duration.ofMinutes(minutes), reason, shouldNotify);
    }

    public static NextSyncSchedule seconds(long seconds, String reason) {
        return seconds(seconds, reason, false);
    }

    public static NextSyncSchedule seconds(long seconds, String reason, boolean shouldNotify) {
        return new NextSyncSchedule(Duration.ofSeconds(seconds), reason, shouldNotify);
    }

    public static NextSyncSchedule hours(long hours, String reason) {
        return hours(hours, reason, false);
    }

    public static NextSyncSchedule hours(long hours, String reason, boolean shouldNotify) {
        return new NextSyncSchedule(Duration.ofHours(hours), reason, shouldNotify);
    }
}
