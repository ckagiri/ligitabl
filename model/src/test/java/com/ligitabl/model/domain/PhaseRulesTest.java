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

    // ---- phaseOfTypeContaining ----

    @Test
    void phaseOfTypeContaining_sprint_findsContainingSprint() {
        assertThat(PhaseRules.phaseOfTypeContaining(phases, PhaseType.SPRINT, 12))
                .contains(s("S3"));
    }

    @Test
    void phaseOfTypeContaining_quarter_findsContainingQuarter() {
        assertThat(PhaseRules.phaseOfTypeContaining(phases, PhaseType.QUARTER, 12))
                .contains(s("Q2"));
    }

    @Test
    void phaseOfTypeContaining_matchesSprintContaining() {
        assertThat(PhaseRules.phaseOfTypeContaining(phases, PhaseType.SPRINT, 7))
                .isEqualTo(PhaseRules.sprintContaining(phases, 7));
    }

    // ---- findByCode ----

    @Test
    void findByCode_isCaseInsensitiveAndTypeAgnostic() {
        assertThat(PhaseRules.findByCode(phases, "q2")).contains(s("Q2"));
        assertThat(PhaseRules.findByCode(phases, "FS")).contains(s("FS"));
        assertThat(PhaseRules.findByCode(phases, "s3")).contains(s("S3"));
    }

    @Test
    void findByCode_unknownCode_isEmpty() {
        assertThat(PhaseRules.findByCode(phases, "NOPE")).isEmpty();
    }

    // ---- effectivePosition ----

    @Test
    void effectivePosition_finalizedRound_usesItsOwnPosition() {
        Round round = Round.builder().position(12).finalized(true).build();
        assertThat(PhaseRules.effectivePosition(round)).isEqualTo(12);
    }

    @Test
    void effectivePosition_unfinalizedRoundPastRoundOne_stepsBackOne() {
        Round round = Round.builder().position(12).finalized(false).build();
        assertThat(PhaseRules.effectivePosition(round)).isEqualTo(11);
    }

    @Test
    void effectivePosition_unfinalizedRoundOne_staysAtOne() {
        Round round = Round.builder().position(1).finalized(false).build();
        assertThat(PhaseRules.effectivePosition(round)).isEqualTo(1);
    }

    // ---- resolvePhase ----

    @Test
    void resolvePhase_explicitCode_winsOverDefaultResolution() {
        assertThat(PhaseRules.resolvePhase(phases, "Q1", 12)).isEqualTo(s("Q1"));
    }

    @Test
    void resolvePhase_explicitUnknownCode_isNull() {
        assertThat(PhaseRules.resolvePhase(phases, "NOPE", 12)).isNull();
    }

    @Test
    void resolvePhase_noCode_defaultsToCurrentSprint() {
        assertThat(PhaseRules.resolvePhase(phases, null, 12)).isEqualTo(s("S3"));
    }

    @Test
    void resolvePhase_noCode_nullEffectivePosition_fallsBackToFullSeason() {
        assertThat(PhaseRules.resolvePhase(phases, null, null)).isEqualTo(s("FS"));
    }

    @Test
    void resolvePhase_noCode_positionOutsideAnySprint_fallsBackToQuarterThenFullSeason() {
        // Every round position in the real Premier League fixture falls inside some sprint, so
        // exercise the quarter/full-season fallback with a phase list that has no sprints at all.
        List<RoundSpan> quarterOnly =
                List.of(span("Q1", PhaseType.QUARTER, 1, 9), span("FS", PhaseType.FULL_SEASON, 1, 38));
        assertThat(PhaseRules.resolvePhase(quarterOnly, null, 5)).isEqualTo(span("Q1", PhaseType.QUARTER, 1, 9));
        assertThat(PhaseRules.resolvePhase(quarterOnly, null, 20)).isEqualTo(span("FS", PhaseType.FULL_SEASON, 1, 38));
    }

    private static RoundSpan span(String code, PhaseType type, int from, int to) {
        return RoundSpan.builder()
                .code(code)
                .name(code)
                .type(type)
                .from(from)
                .to(to)
                .build();
    }
}
