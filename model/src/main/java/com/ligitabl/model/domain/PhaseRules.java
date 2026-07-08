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

    /** The sprint that starts at the given round position, e.g. a contest's fromRoundPosition. */
    public static Optional<RoundSpan> sprintStartingAt(List<RoundSpan> phases, int roundPosition) {
        return sprintsOf(phases).stream()
                .filter(s -> s.getFrom() == roundPosition)
                .findFirst();
    }

    /** The sprint that ends at the given round position, e.g. a contest's toRoundPosition. */
    public static Optional<RoundSpan> sprintEndingAt(List<RoundSpan> phases, int roundPosition) {
        return sprintsOf(phases).stream()
                .filter(s -> s.getTo() == roundPosition)
                .findFirst();
    }

    /** The sprint that contains the given round position (from <= roundPosition <= to). */
    public static Optional<RoundSpan> sprintContaining(List<RoundSpan> phases, int roundPosition) {
        return sprintsOf(phases).stream()
                .filter(s -> s.getFrom() <= roundPosition && roundPosition <= s.getTo())
                .findFirst();
    }

    /**
     * True once the current round has reached the start of the sprint containing
     * {@code toRoundPosition} — e.g. a contest's own final sprint. Per-contest, not season-wide.
     */
    public static boolean isFinalSprintUnderway(int toRoundPosition, int currentRoundPosition, List<RoundSpan> phases) {
        return sprintContaining(phases, toRoundPosition)
                .map(finalSprint -> currentRoundPosition >= finalSprint.getFrom())
                .orElse(false);
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
                .orElseThrow(
                        () -> new IllegalStateException("Sprint " + sprint.getCode() + " is not within any quarter"));

        return sprintsOf(phases).stream()
                .filter(s -> s.getTo() == quarter.getTo())
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("Quarter " + quarter.getCode() + " has no closing sprint"));
    }

    /**
     * A short label for a from/to window: a single sprint uses its own code (e.g. "S3"); a span
     * aligned to exactly one quarter uses that quarter's code (e.g. "Q2"); a span covering every
     * quarter uses "FS" (full season); a span aligned to exactly one half of the season (first or
     * second pair of quarters) uses "H1"/"H2"; any other multi-quarter span uses a quarter range
     * (e.g. "Q1-3"). Used both to distinguish repeated contest renewals (e.g. "Homeboyz" →
     * "Homeboyz H2") and to label a contest's own window.
     */
    public static String periodLabel(RoundSpan from, RoundSpan to, List<RoundSpan> phases) {
        if (from.equals(to)) return from.getCode();

        List<RoundSpan> quarters = quartersOf(phases);
        int startIdx = indexOfQuarterContaining(quarters, from.getFrom());
        int endIdx = indexOfQuarterContaining(quarters, to.getTo());
        if (startIdx < 0 || endIdx < 0) return from.getCode() + "-" + to.getCode();

        if (startIdx == endIdx) return quarters.get(startIdx).getCode();

        if (startIdx == 0 && endIdx == quarters.size() - 1) return "FS";

        int span = endIdx - startIdx + 1;
        int halfSize = quarters.size() / 2;
        if (quarters.size() % 2 == 0 && span == halfSize && startIdx % halfSize == 0) {
            return "H" + (startIdx / halfSize + 1);
        }
        return "Q" + (startIdx + 1) + "-" + (endIdx + 1);
    }

    /**
     * {@link #periodLabel(RoundSpan, RoundSpan, List)} resolved directly from round positions
     * (e.g. a contest's or view's from/to), for callers that don't already have the from/to
     * sprints resolved. Null if either round position doesn't align to a sprint boundary.
     */
    public static String resolvePeriodLabel(List<RoundSpan> phases, int fromRoundPosition, int toRoundPosition) {
        return sprintStartingAt(phases, fromRoundPosition)
                .flatMap(from -> sprintEndingAt(phases, toRoundPosition).map(to -> periodLabel(from, to, phases)))
                .orElse(null);
    }

    private static int indexOfQuarterContaining(List<RoundSpan> quarters, int roundPosition) {
        for (int i = 0; i < quarters.size(); i++) {
            RoundSpan quarter = quarters.get(i);
            if (quarter.getFrom() <= roundPosition && roundPosition <= quarter.getTo()) return i;
        }
        return -1;
    }
}
