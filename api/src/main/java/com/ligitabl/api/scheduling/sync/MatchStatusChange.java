package com.ligitabl.api.scheduling.sync;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.ligitabl.model.domain.MatchStatus;

/**
 * Match status transition tracking
 */
public record MatchStatusChange(
        UUID matchId, MatchStatus previousStatus, MatchStatus newStatus, OffsetDateTime changedAt) {}
