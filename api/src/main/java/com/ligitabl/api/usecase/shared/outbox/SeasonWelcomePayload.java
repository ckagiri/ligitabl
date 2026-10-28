package com.ligitabl.api.usecase.shared.outbox;

import java.util.UUID;

public record SeasonWelcomePayload(UUID userId, String userEmail) {}
