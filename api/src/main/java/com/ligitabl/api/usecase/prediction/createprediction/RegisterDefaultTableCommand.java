package com.ligitabl.api.usecase.prediction.createprediction;

import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.league.Season;

/**
 * Writes the round-0 registration a user would have written themselves by submitting the default
 * table during pre-season: same shape, same allowance. Works after predictions open, which a plain
 * {@link CreatePredictionCommand} can't do, since it would resolve to a mid-season join.
 */
public record RegisterDefaultTableCommand(Season season, Contest mainContest) {}
