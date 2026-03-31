package com.ligitabl.api.web.predictions.latestresult;

public record LatestResultResponse(
        int round,
        int score,
        Integer position,
        Integer movement,
        HitDistribution hitDistribution,
        String sprint,
        int sprintFrom,
        int sprintTo) {}
