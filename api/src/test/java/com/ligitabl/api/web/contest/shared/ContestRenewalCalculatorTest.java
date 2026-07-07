package com.ligitabl.api.web.contest.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.RoundSpan;

/**
 * Phases mirror a season's S1-S8 / Q1-Q4 structure, with one round per sprint for compactness:
 * S1=round1 ... S8=round8, Q1=S1+S2, Q2=S3+S4, Q3=S5+S6, Q4=S7+S8.
 */
class ContestRenewalCalculatorTest {

    private final List<RoundSpan> phases = buildPhases();
    private final Map<String, RoundSpan> byCode = phases.stream().collect(Collectors.toMap(RoundSpan::getCode, s -> s));

    private static List<RoundSpan> buildPhases() {
        List<RoundSpan> sprints = List.of(
                sprint("S1", 1, 1),
                sprint("S2", 2, 2),
                sprint("S3", 3, 3),
                sprint("S4", 4, 4),
                sprint("S5", 5, 5),
                sprint("S6", 6, 6),
                sprint("S7", 7, 7),
                sprint("S8", 8, 8));
        List<RoundSpan> quarters = List.of(
                quarter("Q1", 1, 2), quarter("Q2", 3, 4), quarter("Q3", 5, 6), quarter("Q4", 7, 8));
        return java.util.stream.Stream.concat(sprints.stream(), quarters.stream()).toList();
    }

    private static RoundSpan sprint(String code, int from, int to) {
        return RoundSpan.builder().code(code).name(code).type(PhaseType.SPRINT).from(from).to(to).build();
    }

    private static RoundSpan quarter(String code, int from, int to) {
        return RoundSpan.builder().code(code).name(code).type(PhaseType.QUARTER).from(from).to(to).build();
    }

    private RoundSpan s(String code) {
        return byCode.get(code);
    }

    // ---- Section 4: Default TO — Combined Rule ----

    @Test
    void defaultTo_s1ToS2_sameDuration_givesS4() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S2"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S3"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S1"), s("S2"), from, phases)).isEqualTo(s("S4"));
    }

    @Test
    void defaultTo_s3ToS4_sameDuration_givesS6() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S4"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S5"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S3"), s("S4"), from, phases)).isEqualTo(s("S6"));
    }

    @Test
    void defaultTo_s5ToS6_sameDuration_givesS8() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S6"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S7"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S5"), s("S6"), from, phases)).isEqualTo(s("S8"));
    }

    @Test
    void defaultTo_s1ToS4_sameDuration_givesS8() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S4"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S5"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S1"), s("S4"), from, phases)).isEqualTo(s("S8"));
    }

    @Test
    void defaultTo_s3ToS6_exceedsSeason_fallsBackToEndOfQ4() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S6"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S7"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S3"), s("S6"), from, phases)).isEqualTo(s("S8"));
    }

    @Test
    void defaultTo_s1ToS6_exceedsSeason_fallsBackToEndOfQ4() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S6"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S7"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S1"), s("S6"), from, phases)).isEqualTo(s("S8"));
    }

    @Test
    void defaultTo_singleSprint_s4ToS4_givesS5() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S4"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S5"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S4"), s("S4"), from, phases)).isEqualTo(s("S5"));
    }

    @Test
    void defaultTo_singleSprint_s6ToS6_givesS7() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S6"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S7"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S6"), s("S6"), from, phases)).isEqualTo(s("S7"));
    }

    @Test
    void defaultTo_singleSprint_s7ToS7_givesS8() {
        RoundSpan from = ContestRenewalCalculator.resolveRenewalFrom(s("S7"), phases).orElseThrow();
        assertThat(from).isEqualTo(s("S8"));
        assertThat(ContestRenewalCalculator.resolveDefaultTo(s("S7"), s("S7"), from, phases)).isEqualTo(s("S8"));
    }

    @Test
    void resolveRenewalFrom_originalEndsAtS8_isEmpty() {
        assertThat(ContestRenewalCalculator.resolveRenewalFrom(s("S8"), phases)).isEmpty();
    }

    // ---- Section 5: Valid TO Options ----

    @Test
    void validToOptions_fromS3_areS3S4S6S8() {
        assertThat(ContestRenewalCalculator.resolveValidToOptions(s("S3"), phases))
                .containsExactly(s("S3"), s("S4"), s("S6"), s("S8"));
    }

    @Test
    void validToOptions_fromS5_areS5S6S8() {
        assertThat(ContestRenewalCalculator.resolveValidToOptions(s("S5"), phases))
                .containsExactly(s("S5"), s("S6"), s("S8"));
    }

    @Test
    void validToOptions_fromS7_areS7S8() {
        assertThat(ContestRenewalCalculator.resolveValidToOptions(s("S7"), phases))
                .containsExactly(s("S7"), s("S8"));
    }

    @Test
    void validToOptions_fromS8_onlyS8_noDropdownNeeded() {
        // Edge case: only S8 remains after a single-sprint original — TO fixed, no dropdown.
        assertThat(ContestRenewalCalculator.resolveValidToOptions(s("S8"), phases)).containsExactly(s("S8"));
    }

    // ---- Section 2 / 10: Renew button visibility ----

    @Test
    void isRenewable_liveNotFullSeasonNotRenewed_sprintRemains_true() {
        assertThat(ContestRenewalCalculator.isRenewable(s("S1"), s("S2"), phases, true, false))
                .isTrue();
    }

    @Test
    void isRenewable_notLive_false() {
        assertThat(ContestRenewalCalculator.isRenewable(s("S1"), s("S2"), phases, false, false))
                .isFalse();
    }

    @Test
    void isRenewable_alreadyRenewed_false() {
        assertThat(ContestRenewalCalculator.isRenewable(s("S1"), s("S2"), phases, true, true))
                .isFalse();
    }

    @Test
    void isRenewable_fullSeason_s1ToS8_false() {
        assertThat(ContestRenewalCalculator.isRenewable(s("S1"), s("S8"), phases, true, false))
                .isFalse();
    }

    @Test
    void isRenewable_s5ToS8_endOfSeason_noRenewal() {
        assertThat(ContestRenewalCalculator.isRenewable(s("S5"), s("S8"), phases, true, false))
                .isFalse();
    }

    @Test
    void isRenewable_s7ToS8_endOfSeason_noRenewal() {
        assertThat(ContestRenewalCalculator.isRenewable(s("S7"), s("S8"), phases, true, false))
                .isFalse();
    }

    @Test
    void isFullSeason_s1ToS8_true() {
        assertThat(ContestRenewalCalculator.isFullSeason(s("S1"), s("S8"), phases)).isTrue();
    }

    @Test
    void isFullSeason_s1ToS4_false() {
        assertThat(ContestRenewalCalculator.isFullSeason(s("S1"), s("S4"), phases)).isFalse();
    }

    // ---- Renewal timing gate (single sprint: original index + 2; multi-sprint: original's own last sprint) ----

    @Test
    void timingGate_singleSprint_notYetAtOriginalSprint_false() {
        // original = S1 (single sprint), current round is S1 itself
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s("S1"), s("S1"), 1, phases))
                .isFalse();
    }

    @Test
    void timingGate_singleSprint_oneSprintLater_false() {
        // original = S1, current round is S2 — not yet 2 sprints in
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s("S1"), s("S1"), 2, phases))
                .isFalse();
    }

    @Test
    void timingGate_singleSprint_twoSprintsLater_true() {
        // original = S1, current round is S3 — exactly 2 sprints later, enabled
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s("S1"), s("S1"), 3, phases))
                .isTrue();
    }

    @Test
    void timingGate_singleSprint_wellPastThreshold_true() {
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s("S1"), s("S1"), 6, phases))
                .isTrue();
    }

    @Test
    void timingGate_multiSprint_beforeFinalLeg_false() {
        // original = S1-S2 (Q1), current round is S1 — final leg (S2) not underway yet
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s("S1"), s("S2"), 1, phases))
                .isFalse();
    }

    @Test
    void timingGate_multiSprint_finalLegUnderway_true() {
        // original = S1-S2 (Q1), current round is S2 — its own final sprint has begun
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s("S1"), s("S2"), 2, phases))
                .isTrue();
    }

    @Test
    void timingGate_multiSprint_afterFinalLeg_true() {
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s("S1"), s("S2"), 3, phases))
                .isTrue();
    }

    // ---- Single-sprint timing gate against realistic multi-round sprints ----
    //
    // The fixture above uses one round per sprint, which masks a bug: comparing sprint
    // *indices* happens to equal comparing round *positions* only when each sprint is exactly
    // one round long. Real sprints span several rounds (e.g. S1 = GW1-4), so the gate must
    // compare round positions directly, not jump forward by whole sprints.

    private static List<RoundSpan> buildRealisticPhases() {
        List<RoundSpan> sprints = List.of(
                sprint("S1", 1, 4),
                sprint("S2", 5, 9),
                sprint("S3", 10, 13));
        return List.copyOf(sprints);
    }

    @Test
    void timingGate_singleSprint_realisticRounds_s1Gw1ToGw2_false() {
        List<RoundSpan> realistic = buildRealisticPhases();
        RoundSpan s1 = realistic.get(0);
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s1, s1, 1, realistic))
                .isFalse();
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s1, s1, 2, realistic))
                .isFalse();
    }

    @Test
    void timingGate_singleSprint_realisticRounds_s1Gw3_true() {
        // S1 = GW1-4: renewable once the round is 2 rounds after S1's start, i.e. GW3.
        List<RoundSpan> realistic = buildRealisticPhases();
        RoundSpan s1 = realistic.get(0);
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s1, s1, 3, realistic))
                .isTrue();
    }

    @Test
    void timingGate_singleSprint_realisticRounds_s2Gw5ToGw6_false() {
        List<RoundSpan> realistic = buildRealisticPhases();
        RoundSpan s2 = realistic.get(1);
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s2, s2, 5, realistic))
                .isFalse();
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s2, s2, 6, realistic))
                .isFalse();
    }

    @Test
    void timingGate_singleSprint_realisticRounds_s2Gw7_true() {
        // S2 = GW5-9: renewable once the round is 2 rounds after S2's start, i.e. GW7.
        List<RoundSpan> realistic = buildRealisticPhases();
        RoundSpan s2 = realistic.get(1);
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s2, s2, 7, realistic))
                .isTrue();
    }

    @Test
    void timingGate_multiSprint_realisticRounds_lastSprintLive_true() {
        // Contest window S1-S2 (GW1-9, i.e. a full quarter). Current round 7 falls within S2
        // (GW5-9), the contest's own last sprint, so renewal should already be enabled.
        List<RoundSpan> realistic = buildRealisticPhases();
        RoundSpan s1 = realistic.get(0);
        RoundSpan s2 = realistic.get(1);
        assertThat(ContestRenewalCalculator.hasReachedRenewalTiming(s1, s2, 7, realistic))
                .isTrue();
    }

    // ---- Section 7: Past season renewal ----

    @Test
    void pastSeasonWindow_partialOriginal_fromIsS1_defaultToEndOfQ1_editable() {
        var window = ContestRenewalCalculator.resolvePastSeasonWindow(s("S7"), s("S8"), phases);

        assertThat(window.from()).isEqualTo(s("S1"));
        assertThat(window.defaultTo()).isEqualTo(s("S2"));
        assertThat(window.toEditable()).isTrue();
        assertThat(window.validToOptions()).containsExactly(s("S1"), s("S2"), s("S4"), s("S6"), s("S8"));
    }

    @Test
    void pastSeasonWindow_originalFullSeason_toFixedAtS8_notEditable() {
        var window = ContestRenewalCalculator.resolvePastSeasonWindow(s("S1"), s("S8"), phases);

        assertThat(window.from()).isEqualTo(s("S1"));
        assertThat(window.defaultTo()).isEqualTo(s("S8"));
        assertThat(window.toEditable()).isFalse();
        assertThat(window.validToOptions()).containsExactly(s("S8"));
    }
}
