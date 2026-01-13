package com.ligitabl.api.usecases.match.transitionmatchstatus;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

import com.ligitabl.model.domain.MatchStatus;

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
