package com.ligitabl.api.client.footballdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitionResponse(
		Long id,
		String name,
		String code,
		String type,
		String emblem,
		CurrentSeason currentSeason
) {}
