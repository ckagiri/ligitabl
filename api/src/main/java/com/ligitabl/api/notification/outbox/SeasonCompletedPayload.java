package com.ligitabl.api.notification.outbox;

import java.util.UUID;

/**
 * JSON payload of a SEASON_COMPLETED outbox event — the minimal fact recorded when an admin
 * completes the season. Standings are resolved fresh at processing time.
 */
public record SeasonCompletedPayload(UUID seasonId) {}
