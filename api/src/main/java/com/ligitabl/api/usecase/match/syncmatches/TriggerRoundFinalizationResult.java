package com.ligitabl.api.usecase.match.syncmatches;

import java.util.UUID;

public record TriggerRoundFinalizationResult(
        UUID roundId, int roundPosition, boolean finalized, boolean blocked, String message) {}
