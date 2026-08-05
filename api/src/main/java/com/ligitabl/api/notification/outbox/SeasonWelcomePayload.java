package com.ligitabl.api.notification.outbox;

import java.util.UUID;

public record SeasonWelcomePayload(UUID userId, String userEmail) {}
