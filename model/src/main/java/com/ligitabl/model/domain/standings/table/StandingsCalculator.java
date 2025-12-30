package com.ligitabl.model.domain.standings.table;

import java.util.*;

import com.ligitabl.model.domain.*;
import com.ligitabl.model.domain.standings.ranking.RankingRule;
import com.ligitabl.model.domain.standings.ranking.RankingStrategy;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Calculates team standings from matches and applies ranking rules.
 */
public class StandingsCalculator {

    /**
     * Calculate standings using the provided ranking rule.
     */
    public static List<Standing> calculate(List<Team> teams, List<Match> matches) {
        var rankingRule = RankingStrategy.ENGLISH_PREMIER_LEAGUE.build(matches, teams);
        return calculate(teams, matches, rankingRule);
    }

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

    /**
     * Validates that calculated standings match expected values.
     *
     * @param standings Calculated standings
     * @param finishedMatches Matches used for calculation
     * @param totalTeams Expected number of teams
     * @return true if valid
     */
    public static boolean validate(List<StandingsTeamRank> standings, List<Match> finishedMatches, int totalTeams) {
        if (standings.size() != totalTeams) {
            return false;
        }

        // Count total matches per team
        Map<String, Integer> matchCounts = new HashMap<>();
        for (Match match : finishedMatches) {
            if (match.getStatus() != MatchStatus.FINISHED) continue;

            String homeCode = match.getHomeTeam().getTla();
            String awayCode = match.getAwayTeam().getTla();

            matchCounts.merge(homeCode, 1, Integer::sum);
            matchCounts.merge(awayCode, 1, Integer::sum);
        }

        // Validate each team
        for (StandingsTeamRank rank : standings) {
            String teamCode = rank.getRanking().getCode();
            int expectedPlayed = matchCounts.getOrDefault(teamCode, 0);
            int actualPlayed = rank.getMetadata().getPlayed();

            if (expectedPlayed != actualPlayed) {
                return false;
            }

            // Validate wins + draws + losses = played
            int total = rank.getMetadata().getWon()
                    + rank.getMetadata().getDrawn()
                    + rank.getMetadata().getLost();
            if (total != actualPlayed) {
                return false;
            }
        }

        return true;
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
