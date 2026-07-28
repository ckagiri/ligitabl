package com.ligitabl.api.web.predictions.whatif;

import java.util.HashMap;
import java.util.Map;

import com.ligitabl.api.rest.prediction.whatif.WhatIfResult;
import com.ligitabl.model.domain.StandingsTeamRank;

/**
 * teamCode-keyed maps, deliberately the same shape as {@code UserPredictionViewData}'s
 * standingsMap/pointsMap/goalDifferenceMap, so the client can drop them straight into its
 * existing currentStandings/currentPoints/currentGoalDifference state with no reshaping.
 */
public record WhatIfComputeResponse(
        boolean success,
        Map<String, Integer> standingsMap,
        Map<String, Integer> pointsMap,
        Map<String, Integer> goalDifferenceMap) {

    public static WhatIfComputeResponse from(WhatIfResult result) {
        Map<String, Integer> standingsMap = new HashMap<>();
        Map<String, Integer> pointsMap = new HashMap<>();
        Map<String, Integer> goalDifferenceMap = new HashMap<>();

        for (StandingsTeamRank rank : result.whatIfStandings()) {
            String code = rank.getRanking().getCode();
            standingsMap.put(code, rank.getRanking().getPosition());
            pointsMap.put(code, rank.getMetadata().getPoints());
            goalDifferenceMap.put(code, rank.getMetadata().getGd());
        }

        return new WhatIfComputeResponse(true, standingsMap, pointsMap, goalDifferenceMap);
    }
}
