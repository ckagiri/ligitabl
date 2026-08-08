package com.ligitabl.api.rest.finaltable.shared;

import java.util.List;
import java.util.UUID;

/**
 * Failure modes of the Final Table Predictor. Deliberately close to {@code SwapError}'s vocabulary
 * so the two games read the same, minus everything about rounds and cooldowns.
 */
public sealed interface FinalTableError {

    record SeasonNotFound(UUID seasonId) implements FinalTableError {}

    /**
     * Round 1 is no longer OPEN, or the season is completed: the table is frozen for good.
     *
     * <p>There is deliberately no {@code SeasonNotInPlay} alongside this. A completed season is never
     * {@code IN_PLAY}, so a play-state check would fire first and report "not in play" for a table
     * that is simply locked — and this game stays readable, frozen, for the whole season. One
     * predicate answers "may this be touched", and this is its error.
     */
    record EntryClosed(String roundStatus) implements FinalTableError {}

    record InvalidTeamCode(String code) implements FinalTableError {}

    record TeamsNotFound(String teamACode, String teamBCode) implements FinalTableError {}

    /**
     * The replayed swaps did not produce the order the client expected — another tab has saved in
     * between. The client reloads.
     */
    record OutOfSync(List<String> expectedOrder, List<String> actualOrder) implements FinalTableError {}

    /**
     * An empty batch against a row that already exists. Legal only as the baseline-accepting first
     * save; afterwards there is nothing an empty batch can mean except a double-submit or a retry
     * loop, so it is rejected rather than absorbed.
     */
    record NothingToSave() implements FinalTableError {}

    /** The row has been scored, so it can never be edited again. */
    record AlreadyScored() implements FinalTableError {}
}
