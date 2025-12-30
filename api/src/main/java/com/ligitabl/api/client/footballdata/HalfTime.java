package com.ligitabl.api.client.footballdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HalfTime(
		Integer home,
		Integer away
) {}
