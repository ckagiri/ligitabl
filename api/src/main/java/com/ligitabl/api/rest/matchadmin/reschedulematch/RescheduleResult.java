package com.ligitabl.api.rest.matchadmin.reschedulematch;

import java.time.Instant;
import java.util.UUID;

import com.ligitabl.model.domain.MatchStatus;

import lombok.Builder;
import lombok.Value;

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
