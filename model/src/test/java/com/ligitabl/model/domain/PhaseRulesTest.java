package com.ligitabl.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Phases mirror a season's S1-S8 / Q1-Q4 structure, with one round per sprint for compactness:
 * S1=round1 ... S8=round8, Q1=S1+S2, Q2=S3+S4, Q3=S5+S6, Q4=S7+S8.
 */
class PhaseRulesTest {

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
}
