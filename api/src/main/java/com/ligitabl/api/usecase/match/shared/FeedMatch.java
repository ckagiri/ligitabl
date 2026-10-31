package com.ligitabl.api.usecase.match.shared;

import java.time.OffsetDateTime;

import com.ligitabl.model.domain.league.MatchStatus;

/** A match as the feed reports it. {@code id} is our {@code Match.clientId}; goals are the full-time (or current) score. */
public record FeedMatch(
        long id,
        MatchStatus status,
        OffsetDateTime kickoff,
        Integer matchday,
        Integer minute,
        Integer injuryTime,
        Integer homeGoals,
        Integer awayGoals) {}
