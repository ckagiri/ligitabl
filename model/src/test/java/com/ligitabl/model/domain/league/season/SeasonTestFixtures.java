package com.ligitabl.model.domain.league.season;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.SeasonSlug;

/**
 * Shared fixture helpers for the Season_* tests in this package.
 *
 * <p>Dates follow the real Premier League calendar in {@code seed/src/main/resources/seeding/season.yaml}
 * and the window conventions the app runs under, so {@code Season_lifecycleTest} can walk a real
 * year. The {@code Season_is*Test} truth tables still use {@link RelativeDate}: their point is
 * exhaustiveness over orderings, which calendar dates would lose.
 */
final class SeasonTestFixtures {

    // -- The real calendar -------------------------------------------------------------------

    /** The current year, so these fixtures never read as one season's stale snapshot. See the api module's {@code TestCalendar}. */
    private static final int SEASON_START_YEAR = Year.now(ZoneOffset.UTC).getValue();

    /** The upcoming season's slug, in the {@code YYYY-YY} form {@code SeasonSlug} requires. */
    static final String SEASON_SLUG = "%d-%02d".formatted(SEASON_START_YEAR, (SEASON_START_YEAR + 1) % 100);

    /** Last season — over by the time {@link #NOW} arrives. */
    static final LocalDate PREVIOUS_SEASON_START = LocalDate.of(SEASON_START_YEAR - 1, 8, 10);

    static final LocalDate PREVIOUS_SEASON_END = LocalDate.of(SEASON_START_YEAR, 5, 20);

    /** The upcoming season — not yet started at {@link #NOW}. */
    static final LocalDate NEXT_SEASON_START = LocalDate.of(SEASON_START_YEAR, 8, 21);

    static final LocalDate NEXT_SEASON_END = LocalDate.of(SEASON_START_YEAR + 1, 5, 30);

    /**
     * When the upcoming season opens for pre-season registration: 1 July.
     *
     * <p>⚠️ {@code UpdateSeasonDatesUseCase} writes the same instant to both seasons, and it means
     * two things. On the outgoing season it is "promote my successor now"
     * ({@code AutoActivateSeasonUseCase}); on the incoming one, "my pre-season has opened"
     * ({@code getSeasonState}).
     */
    static final OffsetDateTime PRE_SEASON_OPENS_AT = utc(LocalDate.of(SEASON_START_YEAR, 7, 1));

    /** Predictions open a week before the first kickoff. */
    static final OffsetDateTime PREDICTIONS_OPEN_AT = utc(NEXT_SEASON_START.minusDays(7));

    // -- The evaluation instant --------------------------------------------------------------

    /**
     * The instant every fixture is built around and the tests evaluate at: midday, halfway through
     * the upcoming season's pre-season. Derived from the calendar above so editing a date moves it
     * too. It must sit:
     *
     * <ul>
     *   <li>after {@link #PREVIOUS_SEASON_END} and before {@link #NEXT_SEASON_START};
     *   <li>between {@link #PRE_SEASON_OPENS_AT} and {@link #PREDICTIONS_OPEN_AT}, so the default
     *       fixture reads PRE_SEASON;
     *   <li>at midday, where UTC and most zones agree on the date; midnight would hide a zone bug in
     *       {@link Season#getSeasonState(Instant)}.
     * </ul>
     *
     * {@code Season_lifecycleTest} pins these.
     */
    static final Instant NOW =
            middayMidwayBetween(PRE_SEASON_OPENS_AT.toLocalDate(), PREDICTIONS_OPEN_AT.toLocalDate());

    /** {@link #NOW} as a calendar date, in the zone {@code getSeasonState} evaluates in. */
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneOffset.UTC);

    private SeasonTestFixtures() {}

    private static Instant middayMidwayBetween(LocalDate from, LocalDate to) {
        return from.plusDays(ChronoUnit.DAYS.between(from, to) / 2)
                .atTime(LocalTime.NOON)
                .toInstant(ZoneOffset.UTC);
    }

    static Season season(boolean completed, OffsetDateTime preSeasonOpensAt, OffsetDateTime predictionsOpenAt) {
        // Completed: both dates past. Upcoming: start in the future. Matches getSeasonState()'s
        // PRE_SEASON/OFF_SEASON boundary checks; use the overload below to vary them.
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
        /** Exactly {@link #NOW}. The windows use strict {@code isAfter}, so this counts as not yet open. */
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
