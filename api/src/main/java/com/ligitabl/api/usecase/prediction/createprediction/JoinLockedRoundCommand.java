package com.ligitabl.api.usecase.prediction.createprediction;

import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Season;

/**
 * Joins a user into the round that just locked, as though they had submitted while it was open.
 * The caller resolves the season, contest and round once for a whole batch; the use case never
 * re-derives the round, which is what keeps the locked round from becoming the next one.
 */
public record JoinLockedRoundCommand(Season season, Contest mainContest, int roundPosition) {}
