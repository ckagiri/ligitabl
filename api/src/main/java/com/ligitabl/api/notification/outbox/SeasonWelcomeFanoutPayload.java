package com.ligitabl.api.notification.outbox;

import java.util.UUID;

/**
 * Written inside the SEASON_IN_PLAY transaction, processed in a later one. That is what keeps
 * "auto-joins committed ⇒ recipients will be welcomed" true while leaving the fan-out its own
 * retry budget.
 */
public record SeasonWelcomeFanoutPayload(UUID seasonId) {}
