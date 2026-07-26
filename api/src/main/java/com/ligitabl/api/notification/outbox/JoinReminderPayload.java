package com.ligitabl.api.notification.outbox;

import java.util.UUID;

/**
 * @param stage the reminder-schedule day count this event fired for (e.g. 1, 4, 11) — carried
 *     through so the rendered subject/copy can vary by how overdue the user is.
 */
public record JoinReminderPayload(UUID userId, String userEmail, int stage) {}
