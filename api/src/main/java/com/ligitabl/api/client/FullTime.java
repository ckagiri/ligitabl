package com.ligitabl.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FullTime(
		Integer home,
		Integer away
) {}
