package com.ligitabl.api.usecases.matchadmin.transitionmatchstatus;

import java.time.Instant;
import java.util.UUID;

import com.ligitabl.model.domain.MatchStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TransitionResult {
    UUID matchId;
    String matchSlug;
    MatchStatus oldStatus;
    MatchStatus newStatus;
    int roundPosition;
    Instant timestamp;
}
