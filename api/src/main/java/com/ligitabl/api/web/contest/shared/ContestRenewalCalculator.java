package com.ligitabl.api.web.contest.shared;

import java.util.List;
import java.util.Optional;

import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.RoundSpan;

/**
 * Pure calculation of contest renewal windows: a renewed contest always starts the sprint
 * immediately after the original ends (no overlap), defaults to the same duration as the
 * original where it fits in the season (otherwise falls back to the end of FROM's quarter), and
 * exposes only quarter-aligned TO options. Takes a competition's phases and an original contest's
 * window; has no repo dependencies.
 */
public final class ContestRenewalCalculator {

    private ContestRenewalCalculator() {}

    public record RenewalWindow(
            RoundSpan from, RoundSpan defaultTo, List<RoundSpan> validToOptions, boolean toEditable) {}

    public static boolean isFullSeason(RoundSpan originalFrom, RoundSpan originalTo, List<RoundSpan> phases) {
        List<RoundSpan> sprints = PhaseRules.sprintsOf(phases);
        if (sprints.isEmpty()) {
            return false;
        }
        return originalFrom.equals(sprints.get(0)) && originalTo.equals(sprints.get(sprints.size() - 1));
    }

    /** original.to + 1: the sprint immediately after the original contest's window. Empty if none remains. */
    public static Optional<RoundSpan> resolveRenewalFrom(RoundSpan originalTo, List<RoundSpan> phases) {
        List<RoundSpan> sprints = PhaseRules.sprintsOf(phases);
        int idx = sprints.indexOf(originalTo);
        if (idx < 0 || idx + 1 >= sprints.size()) {
            return Optional.empty();
        }
        return Optional.of(sprints.get(idx + 1));
    }

    /**
     * Renew button visibility: not full season, and at least one sprint remains after
     * original.to. Callers are responsible for their own "already renewed" / "is live" gates —
     * this is pure sprint-geometry.
     */
    public static boolean isRenewable(RoundSpan originalFrom, RoundSpan originalTo, List<RoundSpan> phases) {
        if (isFullSeason(originalFrom, originalTo, phases)) {
            return false;
        }
        return resolveRenewalFrom(originalTo, phases).isPresent();
    }

    /**
     * Current-season renewal timing gate: the button only becomes actionable once the season has
     * progressed far enough into the original contest. A single-sprint original requires the
     * season to have reached the round two positions after the original sprint's own start round
     * (e.g. original = S1, GW1-4 → enabled from GW3). A multi-sprint original requires the season
     * to have reached the original's own last sprint (its final leg underway). Does not apply to
     * past-season renewal — round positions there belong to a different season's timeline.
     */
    public static boolean hasReachedRenewalTiming(
            RoundSpan originalFrom, RoundSpan originalTo, int currentRoundPosition, List<RoundSpan> phases) {
        List<RoundSpan> sprints = PhaseRules.sprintsOf(phases);
        int duration = sprints.indexOf(originalTo) - sprints.indexOf(originalFrom) + 1;
        int currentSprintIndex = PhaseRules.sprintContaining(phases, currentRoundPosition)
                .map(sprints::indexOf)
                .orElse(-1);
        if (currentSprintIndex < 0) {
            return false;
        }

        if (duration == 1) {
            return currentRoundPosition >= originalFrom.getFrom() + 2;
        }
        return currentSprintIndex >= sprints.indexOf(originalTo);
    }

    /**
     * True once the current round has already reached (or passed) the target renewal sprint's own
     * start round — renewing at this point would create a contest whose window is already partly
     * or fully in the past. Inclusive: currentRoundPosition == from.getFrom() counts as elapsed.
     */
    public static boolean hasRenewalWindowElapsed(RoundSpan from, int currentRoundPosition) {
        return currentRoundPosition >= from.getFrom();
    }

    /**
     * Default TO — combined rule. Same duration as original if it fits within the season;
     * otherwise falls back to the end of FROM's own quarter.
     */
    public static RoundSpan resolveDefaultTo(
            RoundSpan originalFrom, RoundSpan originalTo, RoundSpan from, List<RoundSpan> phases) {
        List<RoundSpan> sprints = PhaseRules.sprintsOf(phases);
        int duration = sprints.indexOf(originalTo) - sprints.indexOf(originalFrom) + 1;
        int fromIndex = sprints.indexOf(from);
        int sameDurationToIndex = fromIndex + duration - 1;

        if (sameDurationToIndex < sprints.size()) {
            return sprints.get(sameDurationToIndex);
        }
        return PhaseRules.endOfQuarterContaining(from, phases);
    }

    /** Valid TO options for a given FROM: from itself, plus every quarter-end sprint after it. */
    public static List<RoundSpan> resolveValidToOptions(RoundSpan from, List<RoundSpan> phases) {
        return PhaseRules.sprintsOf(phases).stream()
                .filter(s -> s.getFrom() >= from.getFrom())
                .filter(s -> PhaseRules.isValidSprintWindow(from, s, phases))
                .toList();
    }

    /**
     * Past season renewal window. FROM is always the new season's first sprint. If the original
     * was a full season, TO is fixed at the last sprint and not editable; otherwise TO defaults
     * to the end of FROM's quarter (same-duration fallback does not apply to a new season) and is
     * editable across all valid options from FROM.
     */
    public static RenewalWindow resolvePastSeasonWindow(
            RoundSpan originalFrom, RoundSpan originalTo, List<RoundSpan> newSeasonPhases) {
        List<RoundSpan> sprints = PhaseRules.sprintsOf(newSeasonPhases);
        RoundSpan from = sprints.get(0);

        if (isFullSeason(originalFrom, originalTo, newSeasonPhases)) {
            RoundSpan to = sprints.get(sprints.size() - 1);
            return new RenewalWindow(from, to, List.of(to), false);
        }

        RoundSpan defaultTo = PhaseRules.endOfQuarterContaining(from, newSeasonPhases);
        List<RoundSpan> validToOptions = resolveValidToOptions(from, newSeasonPhases);
        return new RenewalWindow(from, defaultTo, validToOptions, true);
    }
}
