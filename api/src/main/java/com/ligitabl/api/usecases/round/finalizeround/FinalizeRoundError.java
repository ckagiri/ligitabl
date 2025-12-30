package com.ligitabl.api.usecases.round.finalizeround;

import com.ligitabl.model.domain.RoundStatus;

import java.util.List;
import java.util.UUID;

public sealed interface FinalizeRoundError {
    record RoundNotReady(UUID roundId, String reason) implements FinalizeRoundError {}
    record StandingsValidationFailed(String reason) implements FinalizeRoundError {}
    record ScoringFailed(UUID userId, String reason) implements FinalizeRoundError {}
    record TransactionFailed(String reason) implements FinalizeRoundError {}
    record RoundNotFound(UUID roundId) implements FinalizeRoundError {}
    record SeasonNotFound(UUID seasonId) implements FinalizeRoundError {}
    record RoundNotLocked(UUID roundId, RoundStatus currentStatus) implements FinalizeRoundError {}
    record CancelledMatchesExist(List<UUID> matchIds) implements FinalizeRoundError {}
    record AlreadyFinalized(UUID roundId) implements FinalizeRoundError {}
    record TeamsNotFound() implements FinalizeRoundError {}
}
