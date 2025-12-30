package com.ligitabl.api.client.footballdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

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
