package com.ligitabl.model.domain.league.season;

import static com.ligitabl.model.domain.league.season.SeasonTestFixtures.NOW;
import static com.ligitabl.model.domain.league.season.SeasonTestFixtures.RelativeDate.FUTURE;
import static com.ligitabl.model.domain.league.season.SeasonTestFixtures.RelativeDate.NULL;
import static com.ligitabl.model.domain.league.season.SeasonTestFixtures.RelativeDate.PAST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.season.SeasonTestFixtures.RelativeDate;

class Season_isInactiveTest {

    @ParameterizedTest(name = "completed={0}, preSeasonOpensAt={1}, predictionsOpenAt={2} -> {3}")
    @MethodSource("truthTable")
    void isInactive(
            boolean completed, RelativeDate preSeasonOpensAt, RelativeDate predictionsOpenAt, boolean expected) {
        Season season = SeasonTestFixtures.season(completed, preSeasonOpensAt.resolve(), predictionsOpenAt.resolve());
        assertThat(season.isInactive(NOW)).isEqualTo(expected);
    }

    private static Stream<Arguments> truthTable() {
        return Stream.of(
                // Not completed, predictions FUTURE, pre-season NULL/FUTURE: the "OFF_SEASON
                // !completed" branch's case (active season promoted before its pre-season opened).
                Arguments.of(false, NULL, FUTURE, false),
                Arguments.of(false, FUTURE, FUTURE, false),

                // Every other combination is claimed by one of the other three phases.
                Arguments.of(false, NULL, NULL, false),
                Arguments.of(false, NULL, PAST, false),
                Arguments.of(true, NULL, NULL, false),
                Arguments.of(true, NULL, FUTURE, false),
                Arguments.of(true, NULL, PAST, false),
                Arguments.of(false, FUTURE, NULL, false),
                Arguments.of(false, FUTURE, PAST, false),
                Arguments.of(true, FUTURE, NULL, false),
                Arguments.of(true, FUTURE, FUTURE, false),
                Arguments.of(true, FUTURE, PAST, false),
                Arguments.of(false, PAST, NULL, false),
                Arguments.of(false, PAST, PAST, false),
                Arguments.of(false, PAST, FUTURE, false),
                // completed + preSeasonOpen + predictionsOpen (NULL defaults open, or PAST): the
                // "completed && preSeasonOpen && predictionsOpen" branch claims these as INACTIVE.
                Arguments.of(true, PAST, NULL, true),
                Arguments.of(true, PAST, PAST, true),
                // completed + preSeasonOpen + predictions NOT yet open: PRE_SEASON needs a season not
                // yet started, which a completed season never is — falls through to INACTIVE.
                Arguments.of(true, PAST, FUTURE, true));
    }

    /**
     * The truth table can't reach this route: its fixture puts startDate in the future for every
     * not-completed row. Covers past startDate with neither window open.
     */
    @Test
    void isInactive_reachable_whenPastStartDateButNeitherWindowHasOpened() {
        Season season = SeasonTestFixtures.season(
                false,
                FUTURE.resolve(),
                FUTURE.resolve(),
                SeasonTestFixtures.daysFromToday(-1),
                SeasonTestFixtures.daysFromToday(270));

        assertThat(season.isInactive(NOW)).isTrue();
    }
}
