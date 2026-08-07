package com.ligitabl.api.scheduling.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.ligitabl.api.scheduling.syncmatches.SyncFrequencyCalculator;
import com.ligitabl.api.testsupport.TestCalendar;

/**
 * Ported from .art/testing/SyncFrequencyCalculatorTest.java.
 */
class SyncFrequencyCalculatorTest {

    /**
     * The instant every kickoff here is placed relative to, and the one passed to the calculator.
     *
     * <p>The calculator used to read {@code OffsetDateTime.now()} internally while these fixtures
     * read it again, so "kickoff in exactly 10 minutes" was really "in 10 minutes minus however long
     * the two reads were apart" — which is why the production code carries a ceiling-rounding
     * workaround. One instant for both sides removes the gap, and the exact-boundary cases below
     * (10, 60 minutes, 6 hours) now mean what they say.
     */
    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(TestCalendar.MID_SEASON, ZoneOffset.UTC);

    @Test
    void shouldTriggerImmediateWhenRoundObstructed() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, false, true, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ZERO);
        assertThat(schedule.reason()).containsIgnoringCase("obstructed");
    }

    @Test
    void shouldTriggerImmediateWhenAllMatchesComplete() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, true, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ZERO);
        assertThat(schedule.reason()).contains("All matches complete");
        assertThat(schedule.reason()).contains("trigger finalization");
    }

    @Test
    void shouldPrioritizeAllCompleteOverSeasonComplete() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, true, false, 0, true, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ZERO);
        assertThat(schedule.reason()).contains("All matches complete");
    }

    @Test
    void shouldCheckDailyWhenSeasonComplete() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, false, false, 0, true, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(24));
        assertThat(schedule.reason()).contains("Season complete");
        assertThat(schedule.reason()).contains("daily for new season");
    }

    @Test
    void shouldPrioritizeSeasonCompleteOverNoMatches() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, false, false, 0, true, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(24));
        assertThat(schedule.reason()).contains("Season complete");
    }

    @Test
    void shouldCheckTwiceDailyWhenNoUpcomingMatches() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, false, false, 0, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(12));
        assertThat(schedule.reason()).contains("No upcoming matches");
        assertThat(schedule.reason()).contains("twice daily");
    }

    @Test
    void shouldPrioritizeNoMatchesOverLiveWithMatchCount() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(true, false, null, false, false, 0, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void shouldSyncEvery3MinutesWhenLiveMatches() {
        var schedule =
                SyncFrequencyCalculator.calculateNextSync(true, true, NOW.plusHours(2), false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofSeconds(90));
        assertThat(schedule.reason()).contains("Live matches in progress");
    }

    @Test
    void shouldCheck6HoursWhenNoScheduledMatches() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(6));
        assertThat(schedule.reason()).contains("No scheduled matches");
    }

    @Test
    void shouldCheck6HoursWhenNextKickoffIsNull() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, true, null, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(6));
        assertThat(schedule.reason()).contains("No scheduled matches");
    }

    @Test
    void shouldCheck3MinutesWhenKickoffInPast() {
        var pastKickoff = NOW.minusMinutes(5);

        var schedule =
                SyncFrequencyCalculator.calculateNextSync(false, true, pastKickoff, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(1));
        assertThat(schedule.reason()).contains("passed");
    }

    @Test
    void shouldCheck3MinutesWhenKickoffWithin10Minutes() {
        var imminentKickoff = NOW.plusMinutes(8);

        var schedule =
                SyncFrequencyCalculator.calculateNextSync(false, true, imminentKickoff, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(1));
        assertThat(schedule.reason()).contains("8 minutes");
        assertThat(schedule.reason()).contains("imminent");
    }

    @Test
    void shouldCheck10MinutesWhenKickoffWithin60Minutes() {
        var soonKickoff = NOW.plusMinutes(45);

        var schedule =
                SyncFrequencyCalculator.calculateNextSync(false, true, soonKickoff, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(10));
        assertThat(schedule.reason()).contains("45 minutes");
        assertThat(schedule.reason()).contains("soon");
    }

    @Test
    void shouldCheck1HourWhenKickoffWithin6Hours() {
        var laterTodayKickoff = NOW.plusHours(3);

        var schedule =
                SyncFrequencyCalculator.calculateNextSync(false, true, laterTodayKickoff, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(1));
        assertThat(schedule.reason()).contains("3 hour");
        assertThat(schedule.reason()).contains("later today");
    }

    @Test
    void shouldCheck6HoursWhenKickoffBeyond6Hours() {
        var tomorrowKickoff = NOW.plusHours(24);

        var schedule =
                SyncFrequencyCalculator.calculateNextSync(false, true, tomorrowKickoff, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(6));
        assertThat(schedule.reason()).contains("24 hours");
        assertThat(schedule.reason()).contains("default check");
    }

    @Test
    void shouldHandleExactly10Minutes() {
        var kickoff = NOW.plusMinutes(10);

        var schedule = SyncFrequencyCalculator.calculateNextSync(false, true, kickoff, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldHandleExactly60Minutes() {
        var kickoff = NOW.plusMinutes(60);

        var schedule = SyncFrequencyCalculator.calculateNextSync(false, true, kickoff, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldHandleExactly6Hours() {
        var kickoff = NOW.plusHours(6);

        var schedule = SyncFrequencyCalculator.calculateNextSync(false, true, kickoff, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void shouldWorkWithoutSeasonCompleteParameter() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, false, 0, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void shouldWorkWithoutMatchCountOrSeasonComplete() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(true, true, NOW.plusHours(2), false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void scenarioSaturdayMatchDay() {
        var saturdayAfternoon = NOW.plusHours(5);

        var schedule =
                SyncFrequencyCalculator.calculateNextSync(false, true, saturdayAfternoon, false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(1));
        assertThat(schedule.reason()).contains("5 hour");
    }

    @Test
    void scenarioInternationalBreak() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, false, false, 0, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(12));
        assertThat(schedule.reason()).contains("twice daily");
    }

    @Test
    void scenarioSeasonEnded() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, null, false, false, 0, true, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofHours(24));
        assertThat(schedule.reason()).contains("Season complete");
    }

    @Test
    void scenarioLiveMatchInProgress() {
        var schedule =
                SyncFrequencyCalculator.calculateNextSync(true, true, NOW.plusHours(1), false, false, 10, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofSeconds(90));
        assertThat(schedule.reason()).contains("Live matches");
    }

    @Test
    void shouldCapAt10MinutesWhenSuspendedMatchPresent() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(
                false, true, true, false, NOW.plusHours(8), false, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(10));
        assertThat(schedule.reason()).contains("Suspended match present");
    }

    @Test
    void shouldCapAt30MinutesWhenCancelledMatchPresent() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(
                false, true, false, true, NOW.plusHours(8), false, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(30));
        assertThat(schedule.reason()).contains("Cancelled match present");
    }

    @Test
    void suspendedCapWinsOverCancelledCap() {
        var schedule =
                SyncFrequencyCalculator.calculateNextSync(false, true, true, true, NOW.plusHours(8), false, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(10));
        assertThat(schedule.reason()).contains("Suspended match present");
    }

    @Test
    void imminentKickoffPollingBeatsSuspendedCap() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(
                false, true, true, false, NOW.plusMinutes(8), false, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(1));
        assertThat(schedule.reason()).contains("imminent");
    }

    @Test
    void livePollingBeatsSuspendedCap() {
        var schedule =
                SyncFrequencyCalculator.calculateNextSync(true, true, true, false, NOW.plusHours(2), false, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofSeconds(90));
        assertThat(schedule.reason()).contains("Live matches");
    }

    @Test
    void shouldCapAt10MinutesWhenSuspendedAndNoScheduledMatches() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, true, false, null, false, false, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ofMinutes(10));
        assertThat(schedule.reason()).contains("Suspended match present");
    }

    @Test
    void obstructedRoundStillImmediateWithSuspendedMatch() {
        var schedule = SyncFrequencyCalculator.calculateNextSync(false, false, true, false, null, false, true, NOW);

        assertThat(schedule.delay()).isEqualTo(Duration.ZERO);
        assertThat(schedule.reason()).containsIgnoringCase("obstructed");
    }
}
