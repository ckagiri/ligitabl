package com.ligitabl.api.notification.outbox;

import java.util.UUID;

/**
 * Carries only the season id: eligibility is evaluated fresh at processing time, so nothing
 * about the round or the candidate set is worth freezing into the payload.
 */
public record SeasonInPlayPayload(UUID seasonId) {}
