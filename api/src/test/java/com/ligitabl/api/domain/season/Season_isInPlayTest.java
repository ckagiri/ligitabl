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

class Season_isInPlayTest {

    @ParameterizedTest(name = "completed={0}, predictionsOpenAt={1} -> {2}")
    @MethodSource("cases")
    void isInPlay(boolean completed, RelativeDate predictionsOpenAt, boolean expected) {
        Season season = SeasonTestFixtures.season(completed, null, predictionsOpenAt.resolve());
        assertThat(season.isInPlay()).isEqualTo(expected);
    }

    private static Stream<Arguments> cases() {
        return Stream.of(
                // not completed: null defaults to open (back-compat), past is open, future is not yet
                Arguments.of(false, NULL, true),
                Arguments.of(false, PAST, true),
                Arguments.of(false, FUTURE, false),
                // completed: never "in play", regardless of predictionsOpenAt — this is what makes
                // isOffSeason()/isInPlay() mutually exclusive
                Arguments.of(true, NULL, false),
                Arguments.of(true, PAST, false),
                Arguments.of(true, FUTURE, false));
    }
}
