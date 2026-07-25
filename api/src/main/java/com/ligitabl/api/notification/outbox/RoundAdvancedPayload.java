package com.ligitabl.api.notification.outbox;

import java.util.UUID;

/**
 * JSON payload of a ROUND_ADVANCED outbox event — the minimal fact recorded
 * inside the round-advancement transaction.
 */
public record RoundAdvancedPayload(UUID seasonId, int roundPosition, int currentRoundPosition) {}
