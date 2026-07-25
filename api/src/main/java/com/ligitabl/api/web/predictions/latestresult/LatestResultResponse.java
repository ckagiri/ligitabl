package com.ligitabl.api.web.predictions.latestresult;

import com.ligitabl.model.domain.HitDistribution;

public record LatestResultResponse(
        int round,
        int score,
        Integer position,
        Integer movement,
        HitDistribution hitDistribution,
        String sprint,
        int sprintFrom,
        int sprintTo,
        int sprintBest,
        boolean isNewSprintBest,
        int currentRound,
        int lastRound) {}
