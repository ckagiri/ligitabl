package com.ligitabl.api.scheduling.sync;

import com.ligitabl.model.domain.MatchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Match status transition tracking
 */
public record MatchStatusChange(
        UUID matchId,
        MatchStatus previousStatus,
        MatchStatus newStatus,
        OffsetDateTime changedAt
) {}
