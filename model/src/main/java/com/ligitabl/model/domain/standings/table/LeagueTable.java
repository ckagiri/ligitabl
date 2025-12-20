package com.ligitabl.model.domain.standings.table;

import java.util.*;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.calculator.StandingsCalculator;
import com.ligitabl.model.domain.standings.calculator.StatsCalculator;
import com.ligitabl.model.domain.standings.ranking.RankingStrategy;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Represents a league table with team standings.
 * This class coordinates between different components but delegates
 * the actual work to specialized calculator classes.
 *
 * <p>Thread-safe as all data is immutable after construction.</p>
 */
public class LeagueTable {
    private final Map<UUID, TeamStats> statsMap;
    private final List<Standing> standings;
    private final List<Team> teams;

    /**
     * Creates a new league table.
     *
     * @param teams The teams in the league
     * @param matches The matches (played and unplayed)
     * @param strategy The ranking strategy to use
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if teams is empty or matches reference unknown teams
     */
    public LeagueTable(List<Team> teams, List<Match> matches, RankingStrategy strategy) {
        // Validation
        Objects.requireNonNull(teams, "Teams list cannot be null");
        Objects.requireNonNull(matches, "Matches list cannot be null");
        Objects.requireNonNull(strategy, "Ranking strategy cannot be null");

        if (teams.isEmpty()) {
            throw new IllegalArgumentException("At least one team is required");
        }

        // Validate all teams are valid
        Set<UUID> duplicateCheck = new HashSet<>();
        for (Team team : teams) {
            if (team == null) {
                throw new IllegalArgumentException("Team list contains null team");
            }
            if (!duplicateCheck.add(team.getId())) {
                throw new IllegalArgumentException(String.format("Duplicate team ID: %s", team.getId()));
            }
        }

        // Validate all matches reference valid teams
        Set<UUID> teamIds = teams.stream().map(Team::getId).collect(Collectors.toSet());

        for (Match match : matches) {
            if (match == null) {
                continue; // Skip null matches (will be ignored in calculations anyway)
            }

            if (!teamIds.contains(match.getHomeTeamId())) {
                throw new IllegalArgumentException(String.format(
                        "Match '%s' references unknown home team: %s", match.getId(), match.getHomeTeamId()));
            }
            if (!teamIds.contains(match.getAwayTeamId())) {
                throw new IllegalArgumentException(String.format(
                        "Match '%s' references unknown away team: %s", match.getId(), match.getAwayTeamId()));
            }
        }

        // Store teams
        this.teams = List.copyOf(teams);

        // Compute statistics using the calculator
        List<UUID> teamIdList = teams.stream().map(Team::getId).toList();
        this.statsMap = StatsCalculator.computeStats(teamIdList, matches);

        // Compute standings using the calculator
        this.standings = StandingsCalculator.computeStandings(statsMap.values(), strategy.build(matches, teams));
    }

    /**
     * Gets the standings.
     *
     * @return Immutable list of standings, sorted by position
     */
    public List<Standing> standings() {
        return standings;
    }

    /**
     * Gets statistics for a specific team.
     *
     * @param teamId The team ID
     * @return Optional containing the team's stats, or empty if team not found
     */
    public Optional<TeamStats> statsFor(UUID teamId) {
        return Optional.ofNullable(statsMap.get(teamId));
    }

    /**
     * Gets the teams in the league.
     *
     * @return Immutable list of teams
     */
    public List<Team> teams() {
        return teams;
    }

    /**
     * Gets the leader (team in first place).
     *
     * @return Optional containing the leader, or empty if no standings
     */
    public Optional<Standing> getLeader() {
        return standings.isEmpty() ? Optional.empty() : Optional.of(standings.get(0));
    }

    /**
     * Gets teams in top N positions.
     *
     * @param n Number of top teams to get
     * @return List of standings for top N teams
     */
    public List<Standing> getTopN(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("N must be at least 1");
        }
        return StandingsCalculator.getPositionRange(standings, 1, n);
    }

    /**
     * Gets teams in bottom N positions.
     *
     * @param n Number of bottom teams to get
     * @return List of standings for bottom N teams
     */
    public List<Standing> getBottomN(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("N must be at least 1");
        }
        if (standings.isEmpty()) {
            return List.of();
        }
        int lastPos = standings.get(standings.size() - 1).position();
        int fromPos = Math.max(1, lastPos - n + 1);
        return StandingsCalculator.getPositionRange(standings, fromPos, lastPos);
    }
}
