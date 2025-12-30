package com.ligitabl.model.domain.standings.table;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.formatter.ConsoleFormatter;
import com.ligitabl.model.domain.standings.formatter.StandingFormatter;
import com.ligitabl.model.domain.standings.ranking.RankingRule;
import com.ligitabl.model.domain.standings.ranking.RankingStrategy;
import com.ligitabl.model.domain.standings.stats.Standing;

/**
 * Represents a league table with team standings.
 */
public class LeagueTable {
    private final List<Team> teams;
    private final List<Match> matches;
    private final List<Standing> standings;
    private final Map<UUID, Team> teamMap;

    /**
     * Create a league table using a predefined strategy.
     */
    public LeagueTable(List<Team> teams, List<Match> matches) {
        this(teams, matches, RankingStrategy.ENGLISH_PREMIER_LEAGUE);
    }

    public LeagueTable(List<Team> teams, List<Match> matches, RankingStrategy strategy) {
        this(teams, matches, strategy.build(matches, teams));
    }

    /**
     * Create a league table using a custom ranking rule.
     */
    public LeagueTable(List<Team> teams, List<Match> matches, RankingRule rankingRule) {
        this.teams = Objects.requireNonNull(teams);
        this.matches = Objects.requireNonNull(matches);
        this.standings = StandingsCalculator.calculate(teams, matches, rankingRule);
        this.teamMap = teams.stream().collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    /**
     * Get the standings list.
     */
    public List<Standing> getStandings() {
        return List.copyOf(standings);
    }

    /**
     * Format standings using the specified formatter.
     *
     * Example:
     * <pre>
     * String json = table.format(new JsonFormatter());
     * String csv = table.format(new CsvFormatter());
     * String markdown = table.format(new MarkdownFormatter());
     * </pre>
     */
    public String format(StandingFormatter formatter) {
        Objects.requireNonNull(formatter);
        return formatter.format(standings, teamMap);
    }

    /**
     * Print to console using default ConsoleFormatter.
     */
    public void print() {
        System.out.println(format(new ConsoleFormatter()));
    }

    /**
     * Get team by ID.
     */
    public Team getTeam(UUID teamId) {
        return teamMap.get(teamId);
    }

    /**
     * Get all teams.
     */
    public List<Team> getTeams() {
        return List.copyOf(teams);
    }

    /**
     * Get all matches.
     */
    public List<Match> getMatches() {
        return List.copyOf(matches);
    }
}
