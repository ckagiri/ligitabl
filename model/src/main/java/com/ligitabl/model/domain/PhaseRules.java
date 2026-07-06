package com.ligitabl.model.domain;

import java.util.List;
import java.util.Optional;

/** Shared validation rules for sprint/quarter windows, used by contest creation and renewal. */
public final class PhaseRules {

    private PhaseRules() {}

    public static List<RoundSpan> sprintsOf(List<RoundSpan> phases) {
        return phases.stream().filter(p -> p.getType() == PhaseType.SPRINT).toList();
    }

    public static List<RoundSpan> quartersOf(List<RoundSpan> phases) {
        return phases.stream().filter(p -> p.getType() == PhaseType.QUARTER).toList();
    }

    public static Optional<RoundSpan> findSprintByCode(List<RoundSpan> phases, String code) {
        return sprintsOf(phases).stream()
                .filter(s -> s.getCode().equalsIgnoreCase(code))
                .findFirst();
    }

    public static boolean isQuarterStart(RoundSpan sprint, List<RoundSpan> quarters) {
        return quarters.stream().anyMatch(q -> sprint.getFrom() == q.getFrom() && sprint.getTo() <= q.getTo());
    }

    public static boolean isQuarterEnd(RoundSpan sprint, List<RoundSpan> quarters) {
        return quarters.stream().anyMatch(q -> sprint.getTo() == q.getTo() && sprint.getFrom() >= q.getFrom());
    }

    /**
     * Single sprint (from == to) is always valid. Multi-sprint requires from to be a quarter
     * start, to to be a quarter end, and to to come after from.
     */
    public static boolean isValidSprintWindow(RoundSpan from, RoundSpan to, List<RoundSpan> phases) {
        if (from.getCode().equalsIgnoreCase(to.getCode())) return true;

        List<RoundSpan> quarters = quartersOf(phases);
        boolean toIsAfterFrom = to.getFrom() > from.getTo();

        return isQuarterStart(from, quarters) && isQuarterEnd(to, quarters) && toIsAfterFrom;
    }

    /** The quarter's last sprint (the sprint whose `to` matches the quarter's `to`). */
    public static RoundSpan endOfQuarterContaining(RoundSpan sprint, List<RoundSpan> phases) {
        List<RoundSpan> quarters = quartersOf(phases);
        RoundSpan quarter = quarters.stream()
                .filter(q -> q.getFrom() <= sprint.getFrom() && q.getTo() >= sprint.getTo())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Sprint " + sprint.getCode() + " is not within any quarter"));

        return sprintsOf(phases).stream()
                .filter(s -> s.getTo() == quarter.getTo())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Quarter " + quarter.getCode() + " has no closing sprint"));
    }
}
