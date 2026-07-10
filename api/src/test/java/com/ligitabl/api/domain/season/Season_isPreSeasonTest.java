package com.ligitabl.api.domain.season;

import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.FUTURE;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.NULL;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.PAST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate;
import com.ligitabl.model.domain.Season;

class Season_isPreSeasonTest {

    @ParameterizedTest(name = "completed={0}, preSeasonOpensAt={1}, predictionsOpenAt={2} -> {3}")
    @MethodSource("truthTable")
    void isPreSeason(
            boolean completed, RelativeDate preSeasonOpensAt, RelativeDate predictionsOpenAt, boolean expected) {
        Season season = SeasonTestFixtures.season(completed, preSeasonOpensAt.resolve(), predictionsOpenAt.resolve());
        assertThat(season.isPreSeason()).isEqualTo(expected);
    }

    private static Stream<Arguments> truthTable() {
        return Stream.of(
                // preSeasonOpensAt null or future: isPreSeasonOpen() is false, so isPreSeason() is
                // false regardless of completed/predictionsOpenAt
                Arguments.of(false, NULL, NULL, false),
                Arguments.of(false, NULL, FUTURE, false),
                Arguments.of(false, NULL, PAST, false),
                Arguments.of(true, NULL, NULL, false),
                Arguments.of(true, NULL, FUTURE, false),
                Arguments.of(true, NULL, PAST, false),
                Arguments.of(false, FUTURE, NULL, false),
                Arguments.of(false, FUTURE, FUTURE, false),
                Arguments.of(false, FUTURE, PAST, false),
                Arguments.of(true, FUTURE, NULL, false),
                Arguments.of(true, FUTURE, FUTURE, false),
                Arguments.of(true, FUTURE, PAST, false),

                // preSeasonOpensAt past: isPreSeasonOpen() is true, and isOffSeason() is false
                // (preSeasonOpensAt has already passed, regardless of completed) — so, when not
                // completed, isPreSeason() depends only on whether predictions have opened yet
                Arguments.of(false, PAST, NULL, false), // isInPlay(): null defaults to open
                Arguments.of(false, PAST, PAST, false), // isInPlay(): already open
                Arguments.of(false, PAST, FUTURE, true), // isInPlay(): not yet open

                // completed + preSeasonOpen + predictionsOpen (NULL defaults open, or PAST): the
                // explicit "completed && preSeasonOpen && predictionsOpen" branch in
                // getSeasonState() claims these as INACTIVE instead, so isPreSeason() is false.
                Arguments.of(true, PAST, NULL, false),
                Arguments.of(true, PAST, PAST, false),
                // completed + preSeasonOpen + predictions NOT yet open: PRE_SEASON only applies to
                // an upcoming (not-yet-started) season, so a *completed* season here falls through
                // to INACTIVE instead — not PRE_SEASON.
                Arguments.of(true, PAST, FUTURE, false));
    }
}
