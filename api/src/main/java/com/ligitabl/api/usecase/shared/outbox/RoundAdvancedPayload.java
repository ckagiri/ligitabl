package com.ligitabl.api.usecase.shared.outbox;

import java.util.UUID;

/**
 * JSON payload of a ROUND_ADVANCED outbox event — the minimal fact recorded
 * inside the round-advancement transaction.
 */
public record RoundAdvancedPayload(UUID seasonId, int roundPosition, int currentRoundPosition) {}
