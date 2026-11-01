package com.ligitabl.api.testsupport;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneOffset;

/**
 * The base year and date helpers every test calendar in this module derives from. Callers keep
 * their own month/day boundaries. The model module's {@code SeasonTestFixtures} can't see this
 * class and keeps its own copy of the base year and midpoint rule.
 *
 * <p>The year comes from the clock so fixtures never read as a stale snapshot. That is safe
 * because every date derives from it and every assertion is about ordering, which a whole-year
 * shift preserves; evaluation instants derive from the calendars, so a run is deterministic. To
 * re-check, pin {@link #SEASON_START_YEAR} to a literal far from now and run
 * {@code make test-api-core} and {@code make test-api-it}: the counts must not change. If they do,
 * something depends on the absolute year, and that calendar needs a fixed date instead.
 *
 * <p>⚠️ Read at a fixed zone: {@code Year.now()} uses the JVM default, so a run across New Year's
 * midnight could compute two base years.
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
     * evaluate at, and what {@link FixedClockConfig} freezes its {@code Clock} to. Far from either end,
     * so a test shifting it by hours or days (a 24-hour swap cooldown, {@code .plusMinutes(10)})
     * stays in the same season phase.
     */
    public static final Instant MID_SEASON = middayMidwayBetween(SEASON_START, SEASON_END);

    private TestCalendar() {}

    /** Midday UTC: UTC and most zones agree on the date at noon, so a zone bug can't hide behind midnight. */
    public static Instant middayOn(LocalDate date) {
        return date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
    }

    /** Midday, halfway between two dates, so editing either boundary moves the anchor with it. */
    public static Instant middayMidwayBetween(LocalDate from, LocalDate to) {
        return middayOn(from.plusDays(DAYS.between(from, to) / 2));
    }

    /** {@code YYYY-YY} for a season starting in the given year — the only form {@code SeasonSlug} accepts. */
    public static String seasonSlug(int startYear) {
        return "%d-%02d".formatted(startYear, (startYear + 1) % 100);
    }
}
