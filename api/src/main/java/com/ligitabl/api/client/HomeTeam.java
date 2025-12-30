package com.ligitabl.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HomeTeam(
		Long id,
		String name,
		String shortName,
		String tla,
		String crest
) {}
