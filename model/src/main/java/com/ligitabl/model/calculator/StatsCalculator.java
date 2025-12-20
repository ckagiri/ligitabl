package com.ligitabl.model.calculator;

import java.util.*;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.standings.stats.TeamStats;
import com.ligitabl.model.domain.standings.table.TeamMatchView;

public final class StatsCalculator {
    private StatsCalculator() {
        throw new AssertionError("No instances");
    }

    public static Map<UUID, TeamStats> computeStats(List<UUID> teamIds, List<Match> matches) {
        Objects.requireNonNull(teamIds);
        Objects.requireNonNull(matches);
        if (teamIds.isEmpty()) throw new IllegalArgumentException("At least one team ID required");

        Map<UUID, TeamStats> stats = new HashMap<>();
        for (UUID id : teamIds) {
            if (id == null) throw new IllegalArgumentException("Team ID cannot be null");
            stats.put(id, TeamStats.empty(id));
        }

        for (Match m : matches) {
            if (m == null) continue;
            if (m.getScore() == null) continue; // unplayed

            UUID homeId = m.getHomeTeamId();
            UUID awayId = m.getAwayTeamId();

            // Only consider matches involving our teams
            if (!stats.containsKey(homeId) && !stats.containsKey(awayId)) continue;

            var sc = m.getScore();
            int hg = sc.getHomeGoals();
            int ag = sc.getAwayGoals();

            if (stats.containsKey(homeId)) {
                TeamMatchView hv = new TeamMatchView(homeId, awayId, hg, ag, true);
                stats.put(homeId, stats.get(homeId).withMatch(hv));
            }
            if (stats.containsKey(awayId)) {
                TeamMatchView av = new TeamMatchView(awayId, homeId, ag, hg, false);
                stats.put(awayId, stats.get(awayId).withMatch(av));
            }
        }

        return stats;
    }
}
