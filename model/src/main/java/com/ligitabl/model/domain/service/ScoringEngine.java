package com.ligitabl.model.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.ScoringResult;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;

public class ScoringEngine {

    public ScoringResult calculateScore(
            List<TeamRank> predictions, List<StandingsTeamRank> actualStandings, int maxHitPoints) {
        // Create map for quick lookup
        Map<String, Integer> actualPositions = actualStandings.stream()
                .collect(Collectors.toMap(
                        s -> s.getRanking().getCode(), s -> s.getRanking().getPosition()));

        List<ResultTeamRank> detailedRankings = new ArrayList<>();
        int totalHitPoints = 0;
        int zeroesCount = 0;

        for (TeamRank prediction : predictions) {
            Integer actualPosition = actualPositions.get(prediction.getCode());

            if (actualPosition == null) {
                throw new IllegalStateException("Team not found in standings: " + prediction.getCode());
            }

            int hit = Math.abs(prediction.getPosition() - actualPosition);
            totalHitPoints += hit;

            if (hit == 0) {
                zeroesCount++;
            }

            detailedRankings.add(new ResultTeamRank(prediction, actualPosition, hit));
        }

        // Validate total hit points
        if (totalHitPoints > maxHitPoints) {
            throw new IllegalStateException(String.format(
                    "Total hit points (%d) exceeds max_hit_points (%d). "
                            + "Formula: max_hit_points = 2 × (total_teams/2)²",
                    totalHitPoints, maxHitPoints));
        }

        int score = Math.max(0, maxHitPoints - totalHitPoints);

        return new ScoringResult(detailedRankings, score, zeroesCount);
    }
}
