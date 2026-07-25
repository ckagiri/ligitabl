package com.ligitabl.api.notification.outbox;

import java.util.UUID;

public record RoundLockedPayload(UUID seasonId, UUID roundId, int roundPosition) {}
