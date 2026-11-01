package com.ligitabl.api.scheduling.syncmatches;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.ligitabl.model.domain.league.MatchStatus;

public record MatchStatusChange(
        UUID matchId, MatchStatus previousStatus, MatchStatus newStatus, OffsetDateTime changedAt) {}
