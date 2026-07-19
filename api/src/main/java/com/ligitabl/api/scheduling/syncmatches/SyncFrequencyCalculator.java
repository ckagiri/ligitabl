package com.ligitabl.api.scheduling.syncmatches;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Sync frequency calculator based on match timing
 *
 * Strategy:
 * - All matches complete → Immediate (trigger finalization)
 * - Round obstructed (all terminal but includes cancelled/suspended) → Immediate (trigger admin notification)
 * - Season complete → Every 24 hours (daily check for new season)
 * - No upcoming matches → Every 12 hours (twice daily - international break)
 * - Live matches → Every 90 seconds
 * - Kickoff ≤ 10 min → Every 1 minute
 * - Kickoff ≤ 60 min → Every 10 minutes
 * - Kickoff < 6 hours → Every 1 hour
 * - Default → Every 6 hours
 *
 * Delay caps (applied on top of the kickoff-based schedule; faster polling still wins):
 * - Suspended match present → at most every 10 minutes (may resume any time)
 * - Cancelled match present → at most every 30 minutes
 */
public class SyncFrequencyCalculator {

    /**
     * Calculate next sync schedule based on match state
     *
     * @param hasLiveMatches True if any matches are currently live
     * @param hasScheduledMatches True if any matches are scheduled (not yet started)
     * @param hasSuspendedMatches True if any matches are suspended (may resume any time)
     * @param hasCancelledMatches True if any matches are cancelled
     * @param nextKickoff Time of next scheduled kickoff (can be null)
     * @param allMatchesComplete True if all matches are in terminal state
     * @param matchCount Total number of matches (0 = no upcoming matches)
     * @param seasonComplete True if season is complete (all rounds finalized)
     * @return Next sync schedule with delay and reason
     */
    public static NextSyncSchedule calculateNextSync(
            boolean hasLiveMatches,
            boolean hasScheduledMatches,
            boolean hasSuspendedMatches,
            boolean hasCancelledMatches,
            OffsetDateTime nextKickoff,
            boolean allMatchesComplete,
            boolean roundObstructed,
            int matchCount,
            boolean seasonComplete) {

        // PRIORITY 1: All matches complete - trigger finalization immediately
        if (allMatchesComplete) {
            return NextSyncSchedule.immediate("All matches complete - trigger finalization", true);
        }

        // PRIORITY 1b: Round obstructed - trigger admin notification immediately
        if (roundObstructed) {
            return NextSyncSchedule.immediate("Round obstructed - requires admin resolution", true);
        }

        // PRIORITY 2: Season complete - check daily for new season
        if (seasonComplete) {
            return NextSyncSchedule.hours(24, "Season complete - checking daily for new season", true);
        }

        // PRIORITY 3: No upcoming matches (international break, off-week)
        if (matchCount == 0) {
            return NextSyncSchedule.hours(12, "No upcoming matches - checking twice daily", true);
        }

        var schedule = matchDaySchedule(hasLiveMatches, hasScheduledMatches, nextKickoff);
        return applyBlockingMatchCap(schedule, hasSuspendedMatches, hasCancelledMatches);
    }

    private static NextSyncSchedule matchDaySchedule(
            boolean hasLiveMatches, boolean hasScheduledMatches, OffsetDateTime nextKickoff) {

        // PRIORITY 4: Live matches - sync frequently
        if (hasLiveMatches) {
            return NextSyncSchedule.seconds(90, "Live matches in progress").withPhase(NextSyncSchedule.Phase.LIVE);
        }

        // PRIORITY 5: No scheduled matches remaining (all postponed/cancelled)
        if (!hasScheduledMatches || nextKickoff == null) {
            return NextSyncSchedule.hours(6, "No scheduled matches", true);
        }

        // PRIORITY 6: Calculate time until next kickoff
        var now = OffsetDateTime.now();

        // Use ceiling rounding so tests like now().plusMinutes(X) remain stable even if
        // a few milliseconds elapse between constructing nextKickoff and evaluating now.
        long secondsUntilKickoff = Duration.between(now, nextKickoff).getSeconds();
        long minutesUntilKickoff = secondsUntilKickoff < 0 ? -1 : ceilDiv(secondsUntilKickoff, 60);

        // Handle negative values (kickoff in past - shouldn't happen but be defensive)
        if (minutesUntilKickoff < 0) {
            return NextSyncSchedule.minutes(1, "Kickoff time passed - checking immediately", true);
        }

        // Imminent kickoff (≤ 10 minutes)
        if (minutesUntilKickoff <= 10) {
            return NextSyncSchedule.minutes(1, String.format("Kickoff in %d minutes (imminent)", minutesUntilKickoff))
                    .withPhase(NextSyncSchedule.Phase.IMMINENT);
        }

        // Soon kickoff (≤ 60 minutes)
        if (minutesUntilKickoff <= 60) {
            return NextSyncSchedule.minutes(10, String.format("Kickoff in %d minutes (soon)", minutesUntilKickoff))
                    .withPhase(NextSyncSchedule.Phase.SOON);
        }

        // Later today (< 6 hours)
        if (minutesUntilKickoff < 360) {
            long hoursUntilKickoff = ceilDiv(minutesUntilKickoff, 60);
            return NextSyncSchedule.hours(
                    1,
                    String.format(
                            "Kickoff in %d hour%s (later today)",
                            hoursUntilKickoff, hoursUntilKickoff == 1 ? "" : "s"),
                    true);
        }

        // Default: Later Today, Tomorrow or beyond (≥ 6 hours away)
        long hoursUntilKickoff = ceilDiv(minutesUntilKickoff, 60);
        return NextSyncSchedule.hours(
                6, String.format("Kickoff in %d hours (default check)", hoursUntilKickoff), true);
    }

    /**
     * Cap the delay when suspended/cancelled matches are present.
     * Faster kickoff- or live-driven polling still wins; the sync just never
     * waits longer than the cap while such matches exist.
     */
    private static NextSyncSchedule applyBlockingMatchCap(
            NextSyncSchedule schedule, boolean hasSuspendedMatches, boolean hasCancelledMatches) {

        if (hasSuspendedMatches && schedule.delay().compareTo(Duration.ofMinutes(10)) > 0) {
            return NextSyncSchedule.minutes(10, "Suspended match present - polling for resumption", true);
        }

        if (hasCancelledMatches && schedule.delay().compareTo(Duration.ofMinutes(30)) > 0) {
            return NextSyncSchedule.minutes(30, "Cancelled match present - polling for updates", true);
        }

        return schedule;
    }

    private static long ceilDiv(long numerator, long denominator) {
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be positive");
        }
        if (numerator <= 0) {
            return 0;
        }
        return (numerator + denominator - 1) / denominator;
    }

    /**
     * Assumes no suspended/cancelled matches
     */
    public static NextSyncSchedule calculateNextSync(
            boolean hasLiveMatches,
            boolean hasScheduledMatches,
            OffsetDateTime nextKickoff,
            boolean allMatchesComplete,
            boolean roundObstructed,
            int matchCount,
            boolean seasonComplete) {

        return calculateNextSync(
                hasLiveMatches,
                hasScheduledMatches,
                false,
                false,
                nextKickoff,
                allMatchesComplete,
                roundObstructed,
                matchCount,
                seasonComplete);
    }

    /**
     * Assumes season is not complete
     */
    public static NextSyncSchedule calculateNextSync(
            boolean hasLiveMatches,
            boolean hasScheduledMatches,
            OffsetDateTime nextKickoff,
            boolean allMatchesComplete,
            int matchCount) {

        return calculateNextSync(
                hasLiveMatches,
                hasScheduledMatches,
                nextKickoff,
                allMatchesComplete,
                false,
                matchCount,
                false // Assume season not complete
                );
    }

    /**
     * Assumes at least 1 match and season not complete
     */
    public static NextSyncSchedule calculateNextSync(
            boolean hasLiveMatches,
            boolean hasScheduledMatches,
            OffsetDateTime nextKickoff,
            boolean allMatchesComplete) {

        return calculateNextSync(
                hasLiveMatches,
                hasScheduledMatches,
                nextKickoff,
                allMatchesComplete,
                false,
                1, // Assume at least 1 match
                false // Assume season not complete
                );
    }

    /**
     * Assumes at least 1 match and season not complete
     */
    public static NextSyncSchedule calculateNextSync(
            boolean hasLiveMatches,
            boolean hasScheduledMatches,
            boolean hasSuspendedMatches,
            boolean hasCancelledMatches,
            OffsetDateTime nextKickoff,
            boolean allMatchesComplete,
            boolean roundObstructed) {

        return calculateNextSync(
                hasLiveMatches,
                hasScheduledMatches,
                hasSuspendedMatches,
                hasCancelledMatches,
                nextKickoff,
                allMatchesComplete,
                roundObstructed,
                1,
                false);
    }
}
