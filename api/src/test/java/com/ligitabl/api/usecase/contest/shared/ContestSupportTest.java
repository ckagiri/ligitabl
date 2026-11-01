package com.ligitabl.api.usecase.contest.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.CompetitionPhaseFixtures;
import com.ligitabl.model.domain.league.PhaseType;
import com.ligitabl.model.domain.league.RoundSpan;

/** Uses the real Premier League phase structure (see {@link CompetitionPhaseFixtures}). */
class ContestSupportTest {

    // findCurrentPhase reads only its arguments.
    private final ContestSupport support = new ContestSupport(null, null, null, null, null);

    private final Competition competition =
            Competition.builder().phases(CompetitionPhaseFixtures.phases()).build();

    private static Contest window(int from, int to) {
        return Contest.builder().fromRoundPosition(from).toRoundPosition(to).build();
    }

    private Optional<String> currentQuarter(Contest contest, int position) {
        return support.findCurrentPhase(contest, competition, PhaseType.QUARTER, position)
                .map(RoundSpan::getCode);
    }

    @Test
    void quarterContainingThePosition() {
        assertThat(currentQuarter(window(1, 38), 15)).contains("Q2");
    }

    @Test
    void beforeTheWindow_firstQuarterInIt() {
        assertThat(currentQuarter(window(10, 38), 3)).contains("Q2");
    }

    @Test
    void afterTheWindow_lastQuarterInIt() {
        assertThat(currentQuarter(window(1, 19), 30)).contains("Q2");
    }

    @Test
    void noQuarterWhollyInsideTheWindow_empty() {
        assertThat(currentQuarter(window(5, 14), 10)).isEmpty();
    }

    @Test
    void findCurrentSprint_unchanged() {
        assertThat(support.findCurrentSprint(window(1, 38), competition, 12))
                .isEqualTo(CompetitionPhaseFixtures.s("S3"));
    }
}
