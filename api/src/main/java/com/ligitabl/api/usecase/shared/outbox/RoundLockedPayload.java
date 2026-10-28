package com.ligitabl.api.usecase.shared.outbox;

import java.util.UUID;

public record RoundLockedPayload(UUID seasonId, UUID roundId, int roundPosition) {}
