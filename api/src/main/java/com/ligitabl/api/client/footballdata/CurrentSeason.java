package com.ligitabl.api.client.footballdata;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentSeason(
	Long id,
	@JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class) OffsetDateTime startDate,
	@JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class) OffsetDateTime endDate,
	Integer currentMatchday) {}
