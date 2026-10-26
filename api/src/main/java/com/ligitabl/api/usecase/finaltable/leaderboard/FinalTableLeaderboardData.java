package com.ligitabl.api.usecase.finaltable.leaderboard;

import java.util.List;

import com.ligitabl.model.domain.FinalTableEntrant;
import com.ligitabl.model.domain.FinalTableLeaderboardEntry;

/**
 * @param revealed false while nothing is scored — the page shows a "results at the end of the
 *     season" state rather than an empty table
 * @param entries the requested page, already ranked
 * @param displayPositions per-entry position with genuine ties collapsed (=4, =4, 6)
 * @param userEntry the viewer's own standing, or null
 * @param totalEntries scored rows in the season, for pagination
 * @param maxScore the best score obtainable this season — distance ceiling, the per-club bonus
 *     and the champion bonus, so a ranked number can be read against something. Derived from
 *     the season rather than assuming 20 clubs / 425 points
 */
public record FinalTableLeaderboardData(
        boolean revealed,
        List<FinalTableLeaderboardEntry> entries,
        List<String> displayPositions,
        FinalTableLeaderboardEntry userEntry,
        int totalEntries,
        int totalPlayers,
        /** For building per-player public links; the view has no Season to ask. */
        String seasonShorthand,
        Integer maxScore,
        /**
         * Everyone who has entered, for the pre-reveal page — which would otherwise be a count
         * and nothing to look at for the length of a season. Empty once revealed, where
         * {@code entries} says the same and more.
         */
        List<FinalTableEntrant> entrants) {}
