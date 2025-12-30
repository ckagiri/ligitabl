package com.ligitabl.api.client.footballdata;

import com.fasterxml.jackson.annotation.JsonProperty;

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
