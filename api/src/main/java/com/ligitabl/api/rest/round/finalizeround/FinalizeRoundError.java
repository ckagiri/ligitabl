package com.ligitabl.api.rest.round.finalizeround;

import java.util.UUID;

public sealed interface FinalizeRoundError {
    record RoundNotReady(UUID roundId, String reason) implements FinalizeRoundError {}

    record StandingsValidationFailed(String reason) implements FinalizeRoundError {}

    record TransactionFailed(String reason) implements FinalizeRoundError {}

    record RoundNotFound(UUID roundId) implements FinalizeRoundError {}

    record SeasonNotFound(UUID seasonId) implements FinalizeRoundError {}

    record AlreadyFinalized(UUID roundId) implements FinalizeRoundError {}

    record NextRoundNotFound(UUID seasonId, int position) implements FinalizeRoundError {}
}
