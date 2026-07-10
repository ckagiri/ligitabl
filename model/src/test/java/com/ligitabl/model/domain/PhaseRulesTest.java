package com.ligitabl.model.domain;

import static com.ligitabl.model.domain.CompetitionPhaseFixtures.s;
import static com.ligitabl.model.domain.CompetitionPhaseFixtures.sprint;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Uses the real Premier League phase structure (see {@link CompetitionPhaseFixtures}). */
class PhaseRulesTest {

    private final List<RoundSpan> phases = CompetitionPhaseFixtures.phases();

    // ---- periodLabel ----

    @Test
    void periodLabel_singleSprint_givesSprintCode() {
        assertThat(PhaseRules.periodLabel(s("S3"), s("S3"), phases)).isEqualTo("S3");
    }

    @Test
    void periodLabel_singleQuarterSpan_givesQuarterCode() {
        assertThat(PhaseRules.periodLabel(s("S3"), s("S4"), phases)).isEqualTo("Q2");
    }

    @Test
    void periodLabel_firstHalf_givesH1() {
        assertThat(PhaseRules.periodLabel(s("S1"), s("S4"), phases)).isEqualTo("H1");
    }

    @Test
    void periodLabel_secondHalf_givesH2() {
        assertThat(PhaseRules.periodLabel(s("S5"), s("S8"), phases)).isEqualTo("H2");
    }

    @Test
    void periodLabel_multiQuarterSpanNotAHalf_givesQuarterRange() {
        assertThat(PhaseRules.periodLabel(s("S3"), s("S6"), phases)).isEqualTo("Q2-3");
    }

    @Test
    void periodLabel_fullSeason_givesFS() {
        assertThat(PhaseRules.periodLabel(s("S1"), s("S8"), phases)).isEqualTo("FS");
    }

    // ---- deriveWindowStatus ----

    @Test
    void deriveWindowStatus_live_withinRange() {
        assertThat(PhaseRules.deriveWindowStatus(3, 6, 4, phases)).isEqualTo("LIVE");
    }

    @Test
    void deriveWindowStatus_finished_pastRange() {
        assertThat(PhaseRules.deriveWindowStatus(1, 2, 5, phases)).isEqualTo("FINISHED");
    }

    @Test
    void deriveWindowStatus_next_holdsForEntireCurrentSprint_notJustItsLastRound() {
        // Multi-round sprints: S1=1-4, S2=5-8. Window starts at S2.
        List<RoundSpan> multiRoundPhases = List.of(sprint("S1", 1, 4), sprint("S2", 5, 8));

        // Every round within S1 — not just round 4, the one immediately before S2 — reads NEXT.
        assertThat(PhaseRules.deriveWindowStatus(5, 8, 1, multiRoundPhases)).isEqualTo("NEXT");
        assertThat(PhaseRules.deriveWindowStatus(5, 8, 2, multiRoundPhases)).isEqualTo("NEXT");
        assertThat(PhaseRules.deriveWindowStatus(5, 8, 4, multiRoundPhases)).isEqualTo("NEXT");
    }

    @Test
    void deriveWindowStatus_future_whenWindowIsNotTheImmediatelyNextSprint() {
        List<RoundSpan> multiRoundPhases = List.of(sprint("S1", 1, 4), sprint("S2", 5, 8), sprint("S3", 9, 12));

        // Window starts at S3; the sprint after current (S1) is S2, not S3 — FUTURE, not NEXT.
        assertThat(PhaseRules.deriveWindowStatus(9, 12, 2, multiRoundPhases)).isEqualTo("FUTURE");
    }
}
