package com.ligitabl.api.usecases.prediction.finalizeround;

import java.time.Instant;
import java.util.UUID;

public record FinalizationResult(
        UUID roundId,
        int roundPosition,
        int submissionsCreated,
        int resultsCalculated,
        boolean seasonCompleted,
        Instant completedAt
) {}
