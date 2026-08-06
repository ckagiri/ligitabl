package com.ligitabl.api.domain.season;

import static com.ligitabl.api.domain.season.SeasonTestFixtures.NOW;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.EXACTLY_NOW;
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
        assertThat(season.getSeasonState(NOW)).isEqualTo(expected);
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
                SeasonTestFixtures.daysFromToday(-1),
                SeasonTestFixtures.daysFromToday(270));

        assertThat(season.getSeasonState(NOW)).isEqualTo(SeasonState.INACTIVE);
    }

    /**
     * The windows are compared with {@code isAfter}, which is strict, so a season whose
     * predictionsOpenAt is *exactly* the evaluation instant has not opened yet. Untestable before
     * the predicates took an explicit instant — with a wall clock there was no way to name "now"
     * and have the assertion still hold by the time it ran.
     */
    @Test
    void getSeasonState_treatsAWindowOpeningAtThisExactInstantAsNotYetOpen() {
        Season atTheBoundary = SeasonTestFixtures.season(false, PAST.resolve(), EXACTLY_NOW.resolve());
        assertThat(atTheBoundary.getSeasonState(NOW)).isEqualTo(SeasonState.PRE_SEASON);

        // One second later the same season is in play, which is what pins the strictness as
        // deliberate rather than an off-by-one nobody chose.
        assertThat(atTheBoundary.getSeasonState(NOW.plusSeconds(1))).isEqualTo(SeasonState.IN_PLAY);
    }
}
