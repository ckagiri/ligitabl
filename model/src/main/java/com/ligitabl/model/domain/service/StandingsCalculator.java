import com.ligitabl.model.domain.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates league standings from match results.
 * Uses Premier League ranking rules.
 */
public class StandingsCalculator {

    /**
     * Calculates standings from finished matches.
     *
     * @param finishedMatches All FINISHED matches to include
     * @param initialRankings Initial team rankings (for team order)
     * @return Sorted standings
     */
    public List<StandingsTeamRank> calculate(
            List<Match> finishedMatches,
            List<TeamRank> initialRankings
    ) {
        // Validate teams are loaded
        for (Match match : finishedMatches) {
            if (!match.hasTeamsLoaded()) {
                throw new IllegalArgumentException(
                        "Match " + match.getId() + " does not have teams loaded. " +
                                "Use repository method that loads teams (e.g., findFinishedMatchesUpToRoundWithTeams)"
                );
            }
        }
        // Build map of team stats
        Map<String, TeamStats> statsMap = new HashMap<>();

        // Initialize all teams with zero stats
        for (TeamRank teamRank : initialRankings) {
            statsMap.put(teamRank.getCode(), new TeamStats(teamRank.getCode()));
        }

        // Process all finished matches
        for (Match match : finishedMatches) {
            if (match.getStatus() != MatchStatus.FINISHED) {
                continue; // Skip non-finished matches
            }

            String homeCode = match.getHomeTeam().getTla();
            String awayCode = match.getAwayTeam().getTla();

            TeamStats homeStats = statsMap.get(homeCode);
            TeamStats awayStats = statsMap.get(awayCode);

            if (homeStats == null || awayStats == null) {
                log.warn("Match involves unknown team: {} vs {}", homeCode, awayCode);
                continue;
            }

            int homeGoals = match.getScore().getHomeGoals();
            int awayGoals = match.getScore().getAwayGoals();

            // Update stats
            homeStats.played++;
            awayStats.played++;

            homeStats.goalsFor += homeGoals;
            homeStats.goalsAgainst += awayGoals;
            awayStats.goalsFor += awayGoals;
            awayStats.goalsAgainst += homeGoals;

            if (homeGoals > awayGoals) {
                // Home win
                homeStats.won++;
                homeStats.points += 3;
                awayStats.lost++;
            } else if (homeGoals < awayGoals) {
                // Away win
                awayStats.won++;
                awayStats.points += 3;
                homeStats.lost++;
            } else {
                // Draw
                homeStats.drawn++;
                awayStats.drawn++;
                homeStats.points++;
                awayStats.points++;
            }

            // Update goal difference
            homeStats.goalDifference = homeStats.goalsFor - homeStats.goalsAgainst;
            awayStats.goalDifference = awayStats.goalsFor - awayStats.goalsAgainst;
        }

        // Convert to list and sort
        List<TeamStats> statsList = new ArrayList<>(statsMap.values());
        sortByPremierLeagueRules(statsList, finishedMatches);

        // Convert to StandingsTeamRank
        List<StandingsTeamRank> standings = new ArrayList<>();
        for (int i = 0; i < statsList.size(); i++) {
            TeamStats stats = statsList.get(i);

            StandingsTeamRank rank = StandingsTeamRank.builder()
                    .ranking(new TeamRank(stats.teamCode, i + 1))
                    .metadata(new StandingsMetadata(
                            stats.played,
                            stats.won,
                            stats.drawn,
                            stats.lost,
                            stats.points,
                            stats.goalsFor,
                            stats.goalsAgainst,
                            stats.goalDifference
                    ))
                    .build();

            standings.add(rank);
        }

        return standings;
    }

    /**
     * Sorts teams according to Premier League rules:
     * 1. Points (descending)
     * 2. Goal difference (descending)
     * 3. Goals scored (descending)
     * 4. Head-to-head (if tied on all above)
     * 5. Team name (alphabetical)
     */
    private void sortByPremierLeagueRules(List<TeamStats> teams, List<Match> matches) {
        teams.sort((a, b) -> {
            // 1. Points
            if (a.points != b.points) {
                return Integer.compare(b.points, a.points);
            }

            // 2. Goal difference
            if (a.goalDifference != b.goalDifference) {
                return Integer.compare(b.goalDifference, a.goalDifference);
            }

            // 3. Goals scored
            if (a.goalsFor != b.goalsFor) {
                return Integer.compare(b.goalsFor, a.goalsFor);
            }

            // 4. Head-to-head (only if 2 teams tied)
            int h2h = calculateHeadToHead(a.teamCode, b.teamCode, matches);
            if (h2h != 0) {
                return h2h;
            }

            // 5. Team name (alphabetical)
            return a.teamCode.compareTo(b.teamCode);
        });
    }

    /**
     * Calculates head-to-head result between two teams.
     * Returns: negative if team1 better, positive if team2 better, 0 if equal
     */
    private int calculateHeadToHead(String team1Code, String team2Code, List<Match> matches) {
        int team1Points = 0;
        int team1GD = 0;

        for (Match match : matches) {
            if (match.getStatus() != MatchStatus.FINISHED) continue;

            String homeCode = match.getHomeTeam().getTla();
            String awayCode = match.getAwayTeam().getTla();

            boolean isH2H = (homeCode.equals(team1Code) && awayCode.equals(team2Code)) ||
                    (homeCode.equals(team2Code) && awayCode.equals(team1Code));

            if (!isH2H) continue;

            int homeGoals = match.getScore().getHomeGoals();
            int awayGoals = match.getScore().getAwayGoals();

            if (homeCode.equals(team1Code)) {
                // Team1 is home
                team1GD += (homeGoals - awayGoals);
                if (homeGoals > awayGoals) team1Points += 3;
                else if (homeGoals == awayGoals) team1Points += 1;
            } else {
                // Team1 is away
                team1GD += (awayGoals - homeGoals);
                if (awayGoals > homeGoals) team1Points += 3;
                else if (awayGoals == homeGoals) team1Points += 1;
            }
        }

        // Compare head-to-head points first, then GD
        if (team1Points != 0) {
            return -Integer.compare(team1Points, 0); // Negative because more points = better
        }
        return -Integer.compare(team1GD, 0);
    }

    /**
     * Validates that calculated standings match expected values.
     *
     * @param standings Calculated standings
     * @param finishedMatches Matches used for calculation
     * @param totalTeams Expected number of teams
     * @return true if valid
     */
    public boolean validate(
            List<StandingsTeamRank> standings,
            List<Match> finishedMatches,
            int totalTeams
    ) {
        if (standings.size() != totalTeams) {
            log.error("Invalid standings count: expected {}, got {}",
                    totalTeams, standings.size());
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
            int actualPlayed = rank.getMetadata().played();

            if (expectedPlayed != actualPlayed) {
                log.error("Team {} played mismatch: expected {}, got {}",
                        teamCode, expectedPlayed, actualPlayed);
                return false;
            }

            // Validate wins + draws + losses = played
            int total = rank.getMetadata().won() +
                    rank.getMetadata().drawn() +
                    rank.getMetadata().lost();
            if (total != actualPlayed) {
                log.error("Team {} stats don't add up: W+D+L={}, Played={}",
                        teamCode, total, actualPlayed);
                return false;
            }
        }

        return true;
    }

    /**
     * Internal class for accumulating team statistics.
     */
    private static class TeamStats {
        final String teamCode;
        int played = 0;
        int won = 0;
        int drawn = 0;
        int lost = 0;
        int points = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int goalDifference = 0;

        TeamStats(String teamCode) {
            this.teamCode = teamCode;
        }
    }
}
