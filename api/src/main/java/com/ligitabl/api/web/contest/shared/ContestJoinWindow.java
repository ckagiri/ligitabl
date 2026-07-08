package com.ligitabl.api.web.contest.shared;

import java.util.List;
import java.util.function.Supplier;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.RoundStatus;

/** Whether a private contest's join window has closed, independent of its own {@code isOpen} toggle. */
public final class ContestJoinWindow {

    private ContestJoinWindow() {}

    /**
     * Rules (contest from=30, to=38; last sprint from=35, to=38):
     *   pos 29  → open    (before contest starts — early join allowed)
     *   pos 34  → open    (before last sprint)
     *   pos 35 + OPEN     → open   (opening round of last sprint, still joinable)
     *   pos 35 + not OPEN → closed (opening round locked)
     *   pos 36+           → closed (past opening round of last sprint, regardless of status)
     *   pos 39+           → closed (past contest end)
     */
    public static boolean isJoinWindowClosed(
            int toRoundPosition, int currentRoundPosition, List<RoundSpan> phases, RoundStatus roundStatus) {
        if (currentRoundPosition > toRoundPosition) return true;
        if (phases == null) return false;

        RoundSpan endSprint =
                PhaseRules.sprintContaining(phases, toRoundPosition).orElse(null);
        if (endSprint == null) return false;

        // Before the last sprint starts → still joinable
        if (currentRoundPosition < endSprint.getFrom()) return false;

        // Past the opening round of the last sprint → always closed
        if (currentRoundPosition > endSprint.getFrom()) return true;

        // Exactly at the opening round of the last sprint → open only if round is OPEN
        return roundStatus != RoundStatus.OPEN;
    }

    /**
     * {@code statusSupplier} is only invoked when the round's OPEN/locked status must be resolved
     * (exactly at the opening round of the last sprint), so callers can defer resolving it until
     * actually needed.
     */
    public static boolean isJoinWindowClosed(
            int toRoundPosition, Round currentRound, Competition competition, Supplier<RoundStatus> statusSupplier) {
        int pos = currentRound.getPosition();
        List<RoundSpan> phases = competition.getPhases();

        if (pos > toRoundPosition) return true;
        if (phases == null) return false;

        RoundSpan endSprint =
                PhaseRules.sprintContaining(phases, toRoundPosition).orElse(null);
        if (endSprint == null) return false;
        if (pos < endSprint.getFrom()) return false;
        if (pos > endSprint.getFrom()) return true;

        return isJoinWindowClosed(toRoundPosition, pos, phases, statusSupplier.get());
    }
}
