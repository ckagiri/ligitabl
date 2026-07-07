package com.ligitabl.api.web.contest.shared;

import java.util.List;
import java.util.function.Supplier;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
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
     *
     * {@code matchesSupplier} is only invoked when the round's OPEN/locked status must be
     * resolved (exactly at the opening round of the last sprint), so callers can defer fetching
     * matches until actually needed.
     */
    public static boolean isJoinWindowClosed(
            int toRoundPosition, Round currentRound, Competition competition, Supplier<List<Match>> matchesSupplier) {
        int pos = currentRound.getPosition();

        if (pos > toRoundPosition) return true;

        if (competition.getPhases() == null) return false;

        RoundSpan endSprint =
                PhaseRules.sprintContaining(competition.getPhases(), toRoundPosition).orElse(null);

        if (endSprint == null) return false;

        // Before the last sprint starts → still joinable
        if (pos < endSprint.getFrom()) return false;

        // Past the opening round of the last sprint → always closed
        if (pos > endSprint.getFrom()) return true;

        // Exactly at the opening round of the last sprint → open only if round is OPEN
        RoundStatus status =
                currentRound.isFinalized() ? RoundStatus.FINALIZED : currentRound.computeStatus(matchesSupplier.get());
        return status != RoundStatus.OPEN;
    }
}
