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
import com.ligitabl.model.domain.SeasonState;

class Season_getSeasonStateTest {

    @ParameterizedTest(name = "completed={0}, preSeasonOpensAt={1}, predictionsOpenAt={2} -> {3}")
    @MethodSource("truthTable")
    void getSeasonState(
            boolean completed, RelativeDate preSeasonOpensAt, RelativeDate predictionsOpenAt, SeasonState expected) {
        Season season = SeasonTestFixtures.season(completed, preSeasonOpensAt.resolve(), predictionsOpenAt.resolve());
        assertThat(season.getSeasonState()).isEqualTo(expected);
    }

    private static Stream<Arguments> truthTable() {
        return Stream.of(
                Arguments.of(false, NULL, NULL, SeasonState.IN_PLAY),
                // beforeActualStart (fixture: startDate=tomorrow for completed=false) + neither
                // predictions nor pre-season open yet => reclassified OFF_SEASON, not INACTIVE.
                Arguments.of(false, NULL, FUTURE, SeasonState.OFF_SEASON),
                Arguments.of(false, NULL, PAST, SeasonState.IN_PLAY),
                Arguments.of(true, NULL, NULL, SeasonState.OFF_SEASON),
                Arguments.of(true, NULL, FUTURE, SeasonState.OFF_SEASON),
                Arguments.of(true, NULL, PAST, SeasonState.OFF_SEASON),
                Arguments.of(false, FUTURE, NULL, SeasonState.IN_PLAY),
                Arguments.of(false, FUTURE, FUTURE, SeasonState.OFF_SEASON),
                Arguments.of(false, FUTURE, PAST, SeasonState.IN_PLAY),
                Arguments.of(true, FUTURE, NULL, SeasonState.OFF_SEASON),
                Arguments.of(true, FUTURE, FUTURE, SeasonState.OFF_SEASON),
                Arguments.of(true, FUTURE, PAST, SeasonState.OFF_SEASON),
                Arguments.of(false, PAST, NULL, SeasonState.IN_PLAY),
                Arguments.of(false, PAST, PAST, SeasonState.IN_PLAY),
                Arguments.of(false, PAST, FUTURE, SeasonState.PRE_SEASON),
                // completed + preSeasonOpen + predictionsOpen (NULL defaults open, or PAST): the
                // explicit "completed && preSeasonOpen && predictionsOpen" branch claims these as
                // INACTIVE — a completed season isn't genuinely "pre-season" once predictions have
                // already opened too, regardless of how preSeasonOpensAt/predictionsOpenAt compare.
                Arguments.of(true, PAST, NULL, SeasonState.INACTIVE),
                Arguments.of(true, PAST, PAST, SeasonState.INACTIVE),
                // completed + preSeasonOpen + predictions NOT yet open: PRE_SEASON only applies to
                // an upcoming (not-yet-started) season (beforeActualStart), which a completed season
                // can never be — falls through to INACTIVE instead.
                Arguments.of(true, PAST, FUTURE, SeasonState.INACTIVE));
    }

    /** See Season_isInactiveTest for why most rows of the truth table above can never reach INACTIVE. */
    @Test
    void getSeasonState_reachesInactive_whenPastStartDateButNeitherWindowHasOpened() {
        Season season = SeasonTestFixtures.season(
                false,
                FUTURE.resolve(),
                FUTURE.resolve(),
                java.time.LocalDate.now().minusDays(1),
                java.time.LocalDate.now().plusMonths(9));

        assertThat(season.getSeasonState()).isEqualTo(SeasonState.INACTIVE);
    }
}
