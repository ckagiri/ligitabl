package com.ligitabl.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentSeason(
		Long id,
		OffsetDateTime startDate,
		OffsetDateTime endDate,
		Integer currentMatchday
) {}
