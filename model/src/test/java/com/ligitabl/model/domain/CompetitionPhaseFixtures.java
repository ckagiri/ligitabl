package com.ligitabl.model.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Premier League's real phase structure from
 * seed/src/main/resources/seeding/competition.yaml: a 38-round season, S1-S8 sprints of uneven
 * length, Q1-Q4 quarters, and the full-season phase. Shared by every test (in this module and,
 * via the model test-jar, the api module) that needs realistic (not one-round-per-sprint) phases.
 */
public final class CompetitionPhaseFixtures {

    private CompetitionPhaseFixtures() {}

    private static final List<RoundSpan> PHASES = List.of(
            span("FS", PhaseType.FULL_SEASON, 1, 38, "Full Season"),
            span("Q1", PhaseType.QUARTER, 1, 9, "Quarter 1"),
            span("Q2", PhaseType.QUARTER, 10, 19, "Quarter 2"),
            span("Q3", PhaseType.QUARTER, 20, 29, "Quarter 3"),
            span("Q4", PhaseType.QUARTER, 30, 38, "Quarter 4"),
            span("S1", PhaseType.SPRINT, 1, 4, "Sprint 1"),
            span("S2", PhaseType.SPRINT, 5, 9, "Sprint 2"),
            span("S3", PhaseType.SPRINT, 10, 14, "Sprint 3"),
            span("S4", PhaseType.SPRINT, 15, 19, "Sprint 4"),
            span("S5", PhaseType.SPRINT, 20, 24, "Sprint 5"),
            span("S6", PhaseType.SPRINT, 25, 29, "Sprint 6"),
            span("S7", PhaseType.SPRINT, 30, 34, "Sprint 7"),
            span("S8", PhaseType.SPRINT, 35, 38, "Sprint 8"));

    private static final Map<String, RoundSpan> BY_CODE =
            PHASES.stream().collect(Collectors.toMap(RoundSpan::getCode, s -> s));

    public static List<RoundSpan> phases() {
        return PHASES;
    }

    /** Looks up a phase (sprint or quarter) by its code, e.g. "S3" or "Q2". */
    public static RoundSpan s(String code) {
        return BY_CODE.get(code);
    }

    public static RoundSpan sprint(String code, int from, int to) {
        return span(code, PhaseType.SPRINT, from, to, code);
    }

    private static RoundSpan span(String code, PhaseType type, int from, int to, String name) {
        return RoundSpan.builder()
                .code(code)
                .name(name)
                .type(type)
                .from(from)
                .to(to)
                .build();
    }
}
