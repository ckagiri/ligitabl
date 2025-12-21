package com.ligitabl.model.domain.standings.table;

import java.util.*;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.ranking.RankingRule;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Calculates team standings from matches and applies ranking rules.
 */
public class StandingsCalculator {

    /**
     * Calculate standings using the provided ranking rule.
     */
    public static List<Standing> calculate(List<Team> teams, List<Match> matches, RankingRule rankingRule) {
        Objects.requireNonNull(teams, "Teams cannot be null");
        Objects.requireNonNull(matches, "Matches cannot be null");
        Objects.requireNonNull(rankingRule, "Ranking rule cannot be null");

        if (teams.isEmpty()) {
            return List.of();
        }

        // Build stats for each team
        Map<UUID, TeamStats> statsMap = buildStatsMap(teams, matches);

        // Sort by ranking rule
        List<TeamStats> sortedStats = new ArrayList<>(statsMap.values());
        sortedStats.sort(rankingRule::compare);

        // Assign positions
        return assignPositions(sortedStats);
    }

    private static Map<UUID, TeamStats> buildStatsMap(List<Team> teams, List<Match> matches) {
        Map<UUID, TeamStats> statsMap = new HashMap<>();

        // Initialize empty stats for all teams
        for (Team team : teams) {
            statsMap.put(team.getId(), TeamStats.empty(team.getId()));
        }

        // Process all played matches
        for (Match match : matches) {
            if (!match.isPlayed()) continue;

            // Update home team
            match.viewFor(match.getHomeTeamId())
                    .ifPresent(view -> statsMap.computeIfPresent(match.getHomeTeamId(), (k, v) -> v.withMatch(view)));

            // Update away team
            match.viewFor(match.getAwayTeamId())
                    .ifPresent(view -> statsMap.computeIfPresent(match.getAwayTeamId(), (k, v) -> v.withMatch(view)));
        }

        return statsMap;
    }

    private static List<Standing> assignPositions(List<TeamStats> sortedStats) {
        List<Standing> standings = new ArrayList<>();

        for (int i = 0; i < sortedStats.size(); i++) {
            standings.add(new Standing(i + 1, sortedStats.get(i)));
        }

        return standings;
    }
}
