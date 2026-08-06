package com.ligitabl.api.testsupport;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneOffset;

/**
 * The base year and date helpers every test calendar in the suite derives from.
 *
 * <p>Callers keep their own month/day boundaries — the DB-backed integration fixtures use a generic
 * 1 August → 31 May window, while the {@code Season} domain tests use the real Premier League
 * dates. What is shared, and what belongs here, is the base year and the two derivations that were
 * otherwise copied alongside it.
 *
 * <p><b>Why the year is read from the clock at all.</b> Hard-coding it means a fixture that reads
 * as a stale snapshot within a couple of seasons, at which point somebody "helpfully" refreshes it
 * and has to re-check every date that hung off it. Deriving it is safe for one reason worth
 * stating: every date in every calendar is derived from this, and every assertion built on them is
 * about <em>ordering</em> — shifting a whole calendar by a whole number of years preserves ordering
 * exactly. The evaluation instants are derived from the calendars rather than from the clock, so a
 * run stays fully deterministic within itself; only the labels move.
 *
 * <p>That argument was checked rather than assumed, and can be re-checked in two minutes: pin
 * {@link #SEASON_START_YEAR} to a literal well outside the current year — 2019 and 2031 were used —
 * and run {@code make test-api-core} and {@code make test-api-it}. The counts must be identical to
 * the live value. If they are not, something has started depending on the absolute year and that
 * calendar needs a fixed date instead.
 *
 * <p>⚠️ Read at a fixed zone deliberately. {@code Year.now()} uses the JVM default, so a suite
 * running across midnight on 31 December could otherwise compute one base year in a class loaded
 * before midnight and another in a class loaded after — the exact species of time-dependent
 * flakiness these calendars exist to remove.
 */
public final class TestCalendar {

    /** The year the season under test kicks off in. */
    public static final int SEASON_START_YEAR = Year.now(ZoneOffset.UTC).getValue();

    /** The season window every DB-backed integration fixture seeds: 1 August through 31 May. */
    public static final LocalDate SEASON_START = LocalDate.of(SEASON_START_YEAR, 8, 1);

    public static final LocalDate SEASON_END = LocalDate.of(SEASON_START_YEAR + 1, 5, 31);

    /** {@code YYYY-YY} — the only form {@code SeasonSlug} accepts. */
    public static final String SEASON_SLUG = seasonSlug(SEASON_START_YEAR);

    /** The season before {@link #SEASON_SLUG}, for fixtures that need a prior season to exist. */
    public static final String PREVIOUS_SEASON_SLUG = seasonSlug(SEASON_START_YEAR - 1);

    /** {@code YYYY/YY} — the display name. */
    public static final String SEASON_NAME = SEASON_SLUG.replace('-', '/');

    /**
     * Midday, halfway through {@link #SEASON_START}–{@link #SEASON_END}: the instant DB-backed tests
     * evaluate at, and what {@link FixedClockConfig} freezes its {@code Clock} to.
     *
     * <p>It has to sit comfortably inside the window rather than near either end, so that a test
     * shifting it by hours or days (a 24-hour swap cooldown, {@code .plusMinutes(10)}) stays in the
     * same season phase and goes on testing what it means to. The midpoint gives that by
     * construction — edit either boundary and this moves with it.
     */
    public static final Instant MID_SEASON = middayMidwayBetween(SEASON_START, SEASON_END);

    private TestCalendar() {}

    /**
     * Midday UTC on the given date.
     *
     * <p>Midday rather than midnight because UTC and most local zones agree on the calendar date at
     * noon but not at 00:00 — so an anchor at midnight hides zone bugs in any date derivation under
     * test, which is to say the most natural value to write is the one that cannot detect them.
     */
    public static Instant middayOn(LocalDate date) {
        return date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
    }

    /**
     * Midday, halfway between two dates.
     *
     * <p>Anchoring at the midpoint rather than a chosen date keeps an evaluation instant inside its
     * window <em>by construction</em>: editing either boundary moves the anchor with it, instead of
     * silently leaving it outside and changing what every fixture in that calendar means.
     */
    public static Instant middayMidwayBetween(LocalDate from, LocalDate to) {
        return middayOn(from.plusDays(DAYS.between(from, to) / 2));
    }

    /** {@code YYYY-YY} for a season starting in the given year — the only form {@code SeasonSlug} accepts. */
    public static String seasonSlug(int startYear) {
        return "%d-%02d".formatted(startYear, (startYear + 1) % 100);
    }
}
