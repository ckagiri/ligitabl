package com.ligitabl.api.usecase.match.syncmatches;

import java.util.UUID;

import com.ligitabl.api.usecase.round.finalizeround.FinalizeRoundError;

public sealed interface TriggerRoundFinalizationError {
    record SeasonNotFound(String competitionCode) implements TriggerRoundFinalizationError {}

    record RoundNotFound(UUID roundId) implements TriggerRoundFinalizationError {}

    record FinalizationFailed(FinalizeRoundError error) implements TriggerRoundFinalizationError {}
}
