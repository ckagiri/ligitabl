package com.ligitabl.api.usecases.prediction.finalizeround;

import java.util.UUID;

public sealed interface FinalizationError {
    record RoundNotReady(UUID roundId, String reason) implements FinalizationError {}
    record StandingsValidationFailed(String reason) implements FinalizationError {}
    record ScoringFailed(UUID userId, String reason) implements FinalizationError {}
    record TransactionFailed(String reason) implements FinalizationError {}
}
