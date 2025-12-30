package com.ligitabl.api.client.footballdata;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchesResponse(List<MatchDto> matches, Competition competition) {}
