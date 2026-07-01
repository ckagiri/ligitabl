package com.ligitabl.api.rest.round.finalizeround;

import java.util.UUID;

public sealed interface FinalizeRoundError {
    record RoundNotReady(UUID roundId, String reason) implements FinalizeRoundError {}

    record RoundObstructed(UUID roundId, java.util.List<UUID> obstructedMatchIds, String message)
            implements FinalizeRoundError {}

    record StandingsValidationFailed(String reason) implements FinalizeRoundError {}

    record TransactionFailed(String reason) implements FinalizeRoundError {}

    record RoundNotFound(UUID roundId) implements FinalizeRoundError {}

    record SeasonNotFound(UUID seasonId) implements FinalizeRoundError {}

    record AlreadyFinalized(UUID roundId) implements FinalizeRoundError {}

    record NextRoundNotFound(UUID seasonId, int position) implements FinalizeRoundError {}

    /** An explicit refinalize (roundPosition provided) was requested outside setup mode. */
    record NotInSetupMode(UUID seasonId) implements FinalizeRoundError {}

    /** An explicit refinalize targeted a round ahead of the season's current round. */
    record RoundAheadOfCurrent(int roundPosition, int currentRoundPosition) implements FinalizeRoundError {}
}
