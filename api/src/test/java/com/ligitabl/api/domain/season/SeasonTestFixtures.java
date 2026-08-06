package com.ligitabl.api.domain.season;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;

/**
 * Shared fixture helpers for the Season_* tests in this package.
 *
 * <p>Dates come from the real Premier League calendar as seeded in
 * {@code seed/src/main/resources/seeding/season.yaml}, and the two window fields follow the
 * conventions this app is actually operated under (see {@link #PRE_SEASON_OPENS_AT} and
 * {@link #PREDICTIONS_OPEN_AT}). A fixture that matches production is worth more than a tidy one:
 * a reader can tell whether a given case is a situation the app really produces, and
 * {@code Season_lifecycleTest} can walk a season through its actual year.
 *
 * <p><b>The calendar is not a substitute for {@link RelativeDate}.</b> The {@code Season_is*Test}
 * truth tables sweep every combination of {@code (completed × preSeasonOpensAt × predictionsOpenAt)}
 * as PAST/FUTURE/NULL, and that exhaustiveness over <em>orderings</em> is the point of them —
 * rewriting those rows as calendar dates would trade it for readability that belongs here instead.
 * The realism buys one thing the truth tables structurally cannot give: a test of the
 * <em>progression</em>, which is {@code Season_lifecycleTest}. Keep both.
 */
final class SeasonTestFixtures {

    // -- The real calendar -------------------------------------------------------------------

    /**
     * The year the upcoming season kicks off in — the current year, so these fixtures never read as
     * a stale snapshot of one particular season. See {@link TestCalendar} for why deriving it is
     * safe and how to re-check that.
     */
    private static final int SEASON_START_YEAR = TestCalendar.SEASON_START_YEAR;

    /** The upcoming season's slug, in the {@code YYYY-YY} form {@code SeasonSlug} requires. */
    static final String SEASON_SLUG = TestCalendar.seasonSlug(SEASON_START_YEAR);

    /** Last season — over by the time {@link #NOW} arrives. */
    static final LocalDate PREVIOUS_SEASON_START = LocalDate.of(SEASON_START_YEAR - 1, 8, 10);

    static final LocalDate PREVIOUS_SEASON_END = LocalDate.of(SEASON_START_YEAR, 5, 20);

    /** The upcoming season — not yet started at {@link #NOW}. */
    static final LocalDate NEXT_SEASON_START = LocalDate.of(SEASON_START_YEAR, 8, 21);

    static final LocalDate NEXT_SEASON_END = LocalDate.of(SEASON_START_YEAR + 1, 5, 30);

    /**
     * When the upcoming season opens for pre-season registration: 1 July, roughly six weeks after
     * the previous season ends.
     *
     * <p>⚠️ The same instant is written to <em>both</em> seasons — {@code UpdateSeasonDatesUseCase}
     * propagates it onto the incoming season deliberately — and it means two different things
     * depending on which one you read it from. On the outgoing season it is "promote my successor
     * now" ({@code SeasonActivationService}); on the incoming season it is "my pre-season has
     * opened" ({@code getSeasonState}). One handover moment, two readings.
     */
    static final OffsetDateTime PRE_SEASON_OPENS_AT = utc(LocalDate.of(SEASON_START_YEAR, 7, 1));

    /** Predictions open a week before the first kickoff. */
    static final OffsetDateTime PREDICTIONS_OPEN_AT = utc(NEXT_SEASON_START.minusDays(7));

    // -- The evaluation instant --------------------------------------------------------------

    /**
     * The instant every fixture here is built around, and the one the tests pass to the predicates
     * under test: <b>midday, halfway through the upcoming season's pre-season</b> — 23 July of
     * whichever year the suite is run in.
     *
     * <p>Fixed rather than {@code Instant.now()}. These predicates used to read the wall clock
     * internally, so fixtures had no choice but to be relative to real time — which made a test that
     * happened to run across a date boundary flaky by construction, and made exact-boundary cases (a
     * {@code predictionsOpenAt} of *this instant*) impossible to express at all. Both are fixed by
     * {@link Season#getSeasonState(Instant)} taking the instant as an argument; passing a constant
     * here is what cashes that in.
     *
     * <p>Derived rather than written out, because the literal date is not the point — the
     * <em>position in the year</em> is, and it has to satisfy four things at once for this package's
     * fixtures to mean anything:
     *
     * <ul>
     *   <li>after {@link #PREVIOUS_SEASON_END}, so a completed season is genuinely past;
     *   <li>before {@link #NEXT_SEASON_START}, so an upcoming season is genuinely upcoming;
     *   <li>between {@link #PRE_SEASON_OPENS_AT} and {@link #PREDICTIONS_OPEN_AT}, so the default
     *       fixture reads PRE_SEASON — which is also the window the pre-season invite email is sent
     *       in, so the fixture doubles as a realistic setting for that feature's tests;
     *   <li>midday, not midnight: UTC and most local zones agree on the calendar date at midday but
     *       not at midnight, so an anchor at 00:00 would hide a zone bug in
     *       {@link Season#getSeasonState(Instant)}'s date derivation — i.e. the most natural value
     *       to write is the one that cannot detect it.
     * </ul>
     *
     * <p>Taking the midpoint of the pre-season window satisfies all four <em>by construction</em>,
     * so editing the calendar above moves the anchor with it instead of silently invalidating every
     * fixture in the package. {@code Season_lifecycleTest} pins the invariants anyway, for the case
     * where an edit makes them unsatisfiable rather than merely different.
     */
    static final Instant NOW =
            TestCalendar.middayMidwayBetween(PRE_SEASON_OPENS_AT.toLocalDate(), PREDICTIONS_OPEN_AT.toLocalDate());

    /** {@link #NOW} as a calendar date, in the zone {@code getSeasonState} evaluates in. */
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneOffset.UTC);

    private SeasonTestFixtures() {}

    static Season season(boolean completed, OffsetDateTime preSeasonOpensAt, OffsetDateTime predictionsOpenAt) {
        // A completed season's own start/end are both in the past; an upcoming (not-yet-started)
        // season's start is in the future — matches getSeasonState()'s PRE_SEASON/OFF_SEASON
        // boundary checks (past endDate when completed, before startDate when not). At NOW the two
        // real seasons land exactly this way round. Use the overload below to vary them freely.
        LocalDate startDate = completed ? PREVIOUS_SEASON_START : NEXT_SEASON_START;
        LocalDate endDate = completed ? PREVIOUS_SEASON_END : NEXT_SEASON_END;
        return season(completed, preSeasonOpensAt, predictionsOpenAt, startDate, endDate);
    }

    static Season season(
            boolean completed,
            OffsetDateTime preSeasonOpensAt,
            OffsetDateTime predictionsOpenAt,
            LocalDate startDate,
            LocalDate endDate) {
        return Season.builder()
                .clientId(1)
                .competitionId(UUID.randomUUID())
                .name("Test Season")
                .slug(SeasonSlug.of(SEASON_SLUG))
                .startDate(startDate)
                .endDate(endDate)
                .completed(completed)
                .preSeasonOpensAt(preSeasonOpensAt)
                .predictionsOpenAt(predictionsOpenAt)
                .build();
    }

    /** A calendar date relative to {@link #NOW}, for cases the real calendar doesn't cover. */
    static LocalDate daysFromToday(long days) {
        return TODAY.plusDays(days);
    }

    /** Midnight UTC on the given date — how the app stores these window fields. */
    static OffsetDateTime utc(LocalDate date) {
        return OffsetDateTime.of(date, LocalTime.MIDNIGHT, ZoneOffset.UTC);
    }

    /** A relative point in time, named for parameterized-test readability instead of raw timestamps. */
    enum RelativeDate {
        NULL,
        PAST,
        FUTURE,
        /**
         * Exactly {@link #NOW}. The windows are tested with {@code isAfter}, which is strict, so a
         * date landing on this instant counts as *not yet* open. Only expressible since the
         * predicates stopped reading the clock themselves.
         */
        EXACTLY_NOW;

        OffsetDateTime resolve() {
            return switch (this) {
                case NULL -> null;
                case PAST -> at(NOW.minusSeconds(86_400));
                case FUTURE -> at(NOW.plusSeconds(86_400));
                case EXACTLY_NOW -> at(NOW);
            };
        }

        private static OffsetDateTime at(Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
    }
}
