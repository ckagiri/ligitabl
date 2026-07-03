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

class Season_isInactiveTest {

    @ParameterizedTest(name = "completed={0}, preSeasonOpensAt={1}, predictionsOpenAt={2} -> {3}")
    @MethodSource("truthTable")
    void isInactive(
            boolean completed, RelativeDate preSeasonOpensAt, RelativeDate predictionsOpenAt, boolean expected) {
        Season season = SeasonTestFixtures.season(completed, preSeasonOpensAt.resolve(), predictionsOpenAt.resolve());
        assertThat(season.isInactive()).isEqualTo(expected);
    }

    private static Stream<Arguments> truthTable() {
        return Stream.of(
                // Only reachable when: not completed (rules out OFF_SEASON), predictionsOpenAt in
                // the future (rules out IN_PLAY), and preSeasonOpensAt null/future (rules out
                // PRE_SEASON, since that requires preSeasonOpensAt already in the past) — an active
                // season promoted before its own pre-season window has been configured/opened.
                Arguments.of(false, NULL, FUTURE, true),
                Arguments.of(false, FUTURE, FUTURE, true),

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
                Arguments.of(true, PAST, NULL, false),
                Arguments.of(true, PAST, PAST, false),
                Arguments.of(true, PAST, FUTURE, false));
    }
}
