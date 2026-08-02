package com.ligitabl.api.rest.prediction.whatif;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.StandingsTeamRank;

/**
 * @param roundOpen whether the round was still open when this was computed — a closed round's
 *     what-if is projected all the same, but its scores are no longer savable.
 */
public record WhatIfResult(List<StandingsTeamRank> whatIfStandings, UUID roundId, boolean roundOpen) {}
