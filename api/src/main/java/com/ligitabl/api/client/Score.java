package com.ligitabl.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Score(
		Winner winner,
		String duration,
		FullTime fullTime,
		HalfTime halfTime
) {}
