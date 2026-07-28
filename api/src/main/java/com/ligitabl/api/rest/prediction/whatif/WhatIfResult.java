package com.ligitabl.api.rest.prediction.whatif;

import java.util.List;

import com.ligitabl.model.domain.StandingsTeamRank;

public record WhatIfResult(List<StandingsTeamRank> whatIfStandings) {}
