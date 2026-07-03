package com.ligitabl.api.domain.season;

import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.FUTURE;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.NULL;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.PAST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
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
                // completed=false + predictionsOpenAt=FUTURE + preSeasonOpensAt=NULL/FUTURE, with
                // this fixture's beforeActualStart always true for completed=false, is exactly the
                // "OFF_SEASON !completed" branch's target case (an active season promoted before
                // its own pre-season window has been configured/opened) — NOT inactive.
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
                Arguments.of(true, PAST, NULL, false),
                Arguments.of(true, PAST, PAST, false),
                Arguments.of(true, PAST, FUTURE, false));
    }

    /**
     * With this package's shared fixture, beforeActualStart is unconditionally true for every
     * completed=false row (startDate is always tomorrow), so the 18-combination truthTable above
     * can never actually reach INACTIVE — every completed=false case is claimed by IN_PLAY,
     * PRE_SEASON, or the OFF_SEASON !completed branch. INACTIVE only shows up once startDate has
     * already passed (season nominally started) while predictionsOpenAt/preSeasonOpensAt are both
     * still pending — a genuine misconfiguration, tested here with an explicit startDate.
     */
    @Test
    void isInactive_reachable_whenPastStartDateButNeitherWindowHasOpened() {
        Season season = SeasonTestFixtures.season(
                false,
                FUTURE.resolve(),
                FUTURE.resolve(),
                java.time.LocalDate.now().minusDays(1),
                java.time.LocalDate.now().plusMonths(9));

        assertThat(season.isInactive()).isTrue();
    }
}
