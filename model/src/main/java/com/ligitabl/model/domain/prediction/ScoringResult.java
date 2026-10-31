package com.ligitabl.model.domain.prediction;

import java.util.List;

public record ScoringResult(List<ResultTeamRank> detailedRankings, int score, int zeroesCount) {}
