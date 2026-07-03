package com.ligitabl.api.domain.season;

import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.FUTURE;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.NULL;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate.PAST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.ligitabl.api.domain.season.SeasonTestFixtures.RelativeDate;
import com.ligitabl.model.domain.Season;

class Season_isOffSeasonTest {

    @ParameterizedTest(name = "completed={0}, preSeasonOpensAt={1} -> {2}")
    @MethodSource("truthTable")
    void isOffSeason(boolean completed, RelativeDate preSeasonOpensAt, boolean expected) {
        Season season = SeasonTestFixtures.season(completed, preSeasonOpensAt.resolve(), null);
        assertThat(season.isOffSeason()).isEqualTo(expected);
    }

    private static Stream<Arguments> truthTable() {
        return Stream.of(
                // not completed: never off-season, regardless of preSeasonOpensAt
                Arguments.of(false, NULL, false),
                Arguments.of(false, FUTURE, false),
                Arguments.of(false, PAST, false),
                // completed: off-season until preSeasonOpensAt passes — a null preSeasonOpensAt
                // never counts as "opened"
                Arguments.of(true, NULL, true),
                Arguments.of(true, FUTURE, true),
                Arguments.of(true, PAST, false));
    }

    @ParameterizedTest(name = "completed={0}, preSeasonOpensAt={1}, predictionsOpenAt={2}")
    @MethodSource("allInputCombinations")
    void neverOverlapsWithIsPreSeason(
            boolean completed, RelativeDate preSeasonOpensAt, RelativeDate predictionsOpenAt) {
        Season season = SeasonTestFixtures.season(completed, preSeasonOpensAt.resolve(), predictionsOpenAt.resolve());
        assertThat(season.isOffSeason() && season.isPreSeason()).isFalse();
    }

    @ParameterizedTest(name = "completed={0}, preSeasonOpensAt={1}, predictionsOpenAt={2}")
    @MethodSource("allInputCombinations")
    void neverOverlapsWithIsInPlay(boolean completed, RelativeDate preSeasonOpensAt, RelativeDate predictionsOpenAt) {
        Season season = SeasonTestFixtures.season(completed, preSeasonOpensAt.resolve(), predictionsOpenAt.resolve());
        assertThat(season.isOffSeason() && season.isInPlay()).isFalse();
    }

    private static Stream<Arguments> allInputCombinations() {
        List<Arguments> combinations = new ArrayList<>();
        for (boolean completed : new boolean[] {true, false}) {
            for (RelativeDate preSeasonOpensAt : RelativeDate.values()) {
                for (RelativeDate predictionsOpenAt : RelativeDate.values()) {
                    combinations.add(Arguments.of(completed, preSeasonOpensAt, predictionsOpenAt));
                }
            }
        }
        return combinations.stream();
    }
}
