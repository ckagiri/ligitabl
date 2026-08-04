package com.ligitabl.api.notification.outbox;

import java.util.UUID;

/**
 * The address is captured at fan-out time rather than re-read at send time, matching
 * {@link JoinReminderPayload} and {@link RoundResultsPayload}: the recipient set was decided
 * then, and a later profile edit should not silently redirect an already-queued email.
 */
public record SeasonWelcomePayload(UUID userId, String userEmail) {}
