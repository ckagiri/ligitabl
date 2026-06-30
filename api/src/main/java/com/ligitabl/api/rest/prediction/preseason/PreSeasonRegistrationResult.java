package com.ligitabl.api.rest.prediction.preseason;

import java.util.UUID;

public record PreSeasonRegistrationResult(UUID predictionId, UUID entryId, int swapsApplied) {}
