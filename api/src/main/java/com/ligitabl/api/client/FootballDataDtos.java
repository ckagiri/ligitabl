package com.ligitabl.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for Football Data API v4
 * https://api.football-data.org/v4
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchesResponse(
        List<MatchDto> matches,
        Competition competition
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchDto(
        Long id,
        OffsetDateTime utcDate,
        String status,
        Integer matchday,
        String stage,
        HomeTeam homeTeam,
        AwayTeam awayTeam,
        Score score
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record HomeTeam(
        Long id,
        String name,
        String shortName,
        String tla,
        String crest
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record AwayTeam(
        Long id,
        String name,
        String shortName,
        String tla,
        String crest
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record Score(
        Winner winner,
        String duration,
        FullTime fullTime,
        HalfTime halfTime
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record FullTime(
        Integer home,
        Integer away
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record HalfTime(
        Integer home,
        Integer away
) {}

public enum Winner {
    @JsonProperty("HOME_TEAM")
    HOME_TEAM,

    @JsonProperty("AWAY_TEAM")
    AWAY_TEAM,

    @JsonProperty("DRAW")
    DRAW,

    @JsonProperty(value = "null")
    NULL
}

@JsonIgnoreProperties(ignoreUnknown = true)
public record Competition(
        Long id,
        String name,
        String code,
        String type,
        String emblem,
        CurrentSeason currentSeason
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentSeason(
        Long id,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        Integer currentMatchday
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitionResponse(
        Long id,
        String name,
        String code,
        String type,
        String emblem,
        CurrentSeason currentSeason
) {}
