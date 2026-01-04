package com.ligitabl.api.usecases.contest;

import java.util.UUID;

public record JoinContestResult(UUID predictionId, UUID entryId, int atRoundNumber, String message) {}
