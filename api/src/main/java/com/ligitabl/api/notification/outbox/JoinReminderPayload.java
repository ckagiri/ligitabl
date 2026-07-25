package com.ligitabl.api.notification.outbox;

import java.util.UUID;

public record JoinReminderPayload(UUID userId, String userEmail) {}
