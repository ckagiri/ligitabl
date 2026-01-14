package com.ligitabl.api.usecases.matchadmin.reschedulematch;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

import com.ligitabl.model.domain.MatchStatus;

@Value
@Builder
public class RescheduleResult {
    UUID matchId;
    String matchSlug;
    MatchStatus oldStatus;
    MatchStatus newStatus;
    int fromRound;
    int toRound;
    boolean wasPostponed;
    Instant timestamp;
}
