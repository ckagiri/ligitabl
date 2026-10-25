package com.ligitabl.api.usecase.round.finalizeround;

import java.time.Instant;
import java.util.UUID;

public record FinalizeRoundResult(
        UUID roundId,
        int roundPosition,
        int submissionsCreated,
        int resultsCalculated,
        boolean seasonCompleted,
        Instant completedAt) {}
