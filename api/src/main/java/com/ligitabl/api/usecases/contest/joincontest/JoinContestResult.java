package com.ligitabl.api.usecases.contest.joincontest;

import java.util.UUID;

public record JoinContestResult(UUID predictionId, UUID entryId, int atRoundNumber, String message) {}
