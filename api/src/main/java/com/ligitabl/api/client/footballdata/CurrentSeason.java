package com.ligitabl.api.client.footballdata;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentSeason(Long id, OffsetDateTime startDate, OffsetDateTime endDate, Integer currentMatchday) {}
