package com.ligitabl.api.notification.outbox;

import java.util.UUID;

/**
 * JSON payload of a ROUND_FINALIZED outbox event — the minimal fact recorded
 * inside the finalization transaction. Everything else (recipients,
 * placements, per-user payloads) is derived post-commit by
 * {@link RoundResultsEmailEnqueuer} when the relay processes this event.
 */
public record RoundFinalizedPayload(UUID seasonId, int roundPosition, int currentRoundPosition) {}
