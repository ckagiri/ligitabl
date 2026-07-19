package com.ligitabl.api.client.footballdata;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchDto(
        Long id,
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class) OffsetDateTime utcDate,
        String status,
        Integer matchday,
        String stage,
        HomeTeam homeTeam,
        AwayTeam awayTeam,
        Score score,
        Integer minute,
        Integer injuryTime) {}
