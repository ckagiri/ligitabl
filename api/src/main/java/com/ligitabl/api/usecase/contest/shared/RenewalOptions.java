package com.ligitabl.api.usecase.contest.shared;

import java.util.List;

import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.contest.ContestRenewalCalculator;
import com.ligitabl.model.domain.league.PhaseRules;
import com.ligitabl.model.domain.league.RoundSpan;

/** The renew control on a contest row: whether to show it, and the sprint codes it offers. */
public record RenewalOptions(
        boolean visible, boolean enabled, String fromCode, String defaultToCode, List<String> toOptionCodes) {

    public static RenewalOptions hidden() {
        return new RenewalOptions(false, false, null, null, List.of());
    }

    /**
     * Options for a contest in the active season. Ownership and privacy are the caller's to check.
     * A null {@code currentRoundPosition} skips the window check and leaves renewal disabled.
     */
    public static RenewalOptions forCurrentSeason(
            Contest contest, List<RoundSpan> phases, Integer currentRoundPosition) {
        if (contest.getRenewedIntoContestId() != null) return hidden();

        RoundSpan originalFrom = PhaseRules.sprintStartingAt(phases, contest.getFromRoundPosition())
                .orElse(null);
        RoundSpan originalTo =
                PhaseRules.sprintEndingAt(phases, contest.getToRoundPosition()).orElse(null);
        if (originalFrom == null || originalTo == null) return hidden();

        if (!ContestRenewalCalculator.isRenewable(originalFrom, originalTo, phases)) return hidden();

        var renewalFrom = ContestRenewalCalculator.resolveRenewalFrom(originalTo, phases);
        if (renewalFrom.isEmpty()) return hidden();

        RoundSpan from = renewalFrom.get();
        if (currentRoundPosition != null
                && ContestRenewalCalculator.hasRenewalWindowElapsed(from, currentRoundPosition)) {
            return hidden();
        }

        RoundSpan defaultTo = ContestRenewalCalculator.resolveDefaultTo(originalFrom, originalTo, from, phases);
        List<String> toOptionCodes = ContestRenewalCalculator.resolveValidToOptions(from, phases).stream()
                .map(RoundSpan::getCode)
                .toList();
        boolean enabled = currentRoundPosition != null
                && ContestRenewalCalculator.hasReachedRenewalTiming(
                        originalFrom, originalTo, currentRoundPosition, phases);

        return new RenewalOptions(true, enabled, from.getCode(), defaultTo.getCode(), toOptionCodes);
    }
}
