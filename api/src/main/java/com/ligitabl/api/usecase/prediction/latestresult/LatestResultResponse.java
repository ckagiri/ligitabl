package com.ligitabl.api.usecase.prediction.latestresult;

import com.ligitabl.model.domain.prediction.HitDistribution;

import lombok.Builder;

@Builder
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
