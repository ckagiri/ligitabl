package com.ligitabl.model.domain.league.standings.ranking;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.league.Match;
import com.ligitabl.model.domain.league.Team;
import com.ligitabl.model.domain.league.standings.stats.TeamStats;

/**
 * Fluent builder for creating ranking rules.
 * Provides both technical methods (thenDescending) and domain-friendly methods (byMostPoints).
 *
 * Example:
 * <pre>
 * RankingRule rule = new RankingBuilder()
 *     .withMatches(matches)
 *     .withTeams(teams)
 *     .byMostPoints()
 *     .byBestGoalDifference()
 *     .byMostGoalsScored()
 *     .byAlphabetical()
 *     .build();
 * </pre>
 */
public class RankingBuilder {
    private RankingRule rule = (a, b) -> 0;
    private Map<UUID, String> teamShortNames = Map.of();
    private List<Match> matches;

    /**
     * Set the matches for head-to-head calculations.
     */
    public RankingBuilder withMatches(List<Match> matches) {
        this.matches = Objects.requireNonNull(matches);
        return this;
    }

    /**
     * Set the teams for alphabetical ordering.
     */
    public RankingBuilder withTeams(List<Team> teams) {
        Objects.requireNonNull(teams);
        this.teamShortNames = teams.stream().collect(Collectors.toMap(Team::getId, Team::getShortName));
        return this;
    }

    public RankingBuilder withTeamShortNames(Map<UUID, String> teamShortNames) {
        this.teamShortNames = Objects.requireNonNull(teamShortNames);
        return this;
    }

    // ========== TECHNICAL METHODS (for power users) ==========

    public RankingBuilder then(RankingRule next) {
        Objects.requireNonNull(next);
        rule = rule.then(next);
        return this;
    }

    public RankingBuilder thenDescending(Function<TeamStats, Integer> extractor) {
        Objects.requireNonNull(extractor);
        return then(RankingRule.descending(extractor));
    }

    public RankingBuilder thenAscending(Function<TeamStats, String> extractor) {
        Objects.requireNonNull(extractor);
        return then(RankingRule.ascending(extractor));
    }

    /**
     * Add ascending alphabetical ordering by team short name.
     */
    public RankingBuilder thenAscendingByShortName() {
        return then((a, b) -> {
            String nameA = teamShortNames.getOrDefault(a.teamId(), a.teamId().toString());
            String nameB = teamShortNames.getOrDefault(b.teamId(), b.teamId().toString());
            return nameA.compareTo(nameB);
        });
    }

    /**
     * Rank by most points (higher is better).
     * This is the primary ranking criterion in soccer.
     */
    public RankingBuilder byMostPoints() {
        return thenDescending(TeamStats::points);
    }

    public RankingBuilder byBestGoalDifference() {
        return thenDescending(TeamStats::goalDiff);
    }

    public RankingBuilder byMostGoalsScored() {
        return thenDescending(TeamStats::goalsFor);
    }

    /**
     * Rank by most away goals (higher is better).
     * Used in UEFA and some head-to-head tie-breaking.
     */
    public RankingBuilder byMostAwayGoals() {
        return thenDescending(TeamStats::awayGoals);
    }

    /**
     * Rank alphabetically by team name (A-Z).
     * This ensures no tied positions.
     */
    public RankingBuilder byAlphabetical() {
        return thenAscendingByShortName();
    }

    /**
     * Apply head-to-head mini-table for tied teams.
     * Requires matches to be set via withMatches().
     */
    public RankingBuilder byHeadToHead() {
        if (matches == null) {
            throw new IllegalStateException("Matches required for head-to-head. Call withMatches() first.");
        }

        // Default H2H tiebreaker: points → goal diff → goals for
        RankingRule h2hTieBreaker = new RankingBuilder()
                .withTeamShortNames(teamShortNames)
                .byMostPoints()
                .byBestGoalDifference()
                .byMostGoalsScored()
                .build();

        return then(new CachedHeadToHeadRule(matches, h2hTieBreaker));
    }

    /**
     * Apply head-to-head with away goals priority.
     * Requires matches to be set via withMatches().
     */
    public RankingBuilder byHeadToHeadAwayGoals() {
        if (matches == null) {
            throw new IllegalStateException("Matches required for head-to-head. Call withMatches() first.");
        }

        // H2H tiebreaker with away goals: points → goal diff → away goals
        RankingRule h2hTieBreaker = new RankingBuilder()
                .withTeamShortNames(teamShortNames)
                .byMostPoints()
                .byBestGoalDifference()
                .byMostAwayGoals()
                .build();

        return then(new CachedHeadToHeadRule(matches, h2hTieBreaker));
    }

    public RankingRule build() {
        return rule;
    }
}
