package com.ligitabl.model.domain.standings.ranking;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Predefined ranking strategies for league tables.
 * Each strategy defines how teams are ranked when they have equal points.
 */
public enum RankingStrategy {

    /**
     * Standard ranking: Points → Goal Difference → Goals For → Alphabetical (by short name)
     */
    STANDARD {
        @Override
        public RankingRule build(List<Match> matches, List<Team> teams) {
            Map<UUID, String> teamNames = extractShortNames(teams);

            return new RankingBuilder()
                    .withTeamNames(teamNames)
                    .thenDescending(TeamStats::points)
                    .thenDescending(TeamStats::goalDiff)
                    .thenDescending(TeamStats::goalsFor)
                    .thenAscendingByName()
                    .build();
        }
    },

    /**
     * Head-to-head ranking with caching for performance.
     * When teams are tied on points, compares their head-to-head record.
     * Falls back to goal difference, goals for, and alphabetical.
     */
    HEAD_TO_HEAD {
        @Override
        public RankingRule build(List<Match> matches, List<Team> teams) {
            Objects.requireNonNull(matches, "Matches cannot be null for H2H strategy");
            var teamNames = extractShortNames(teams);

            // H2H tiebreaker: points in H2H games, then goal diff, then goals for
            var h2hTieBreaker = new RankingBuilder()
                    .thenDescending(TeamStats::points)
                    .thenDescending(TeamStats::goalDiff)
                    .thenDescending(TeamStats::goalsFor)
                    .build();

            // Performance optimization: use cached version
            RankingRule h2hRule = new CachedHeadToHeadRule(matches, h2hTieBreaker);

            return new RankingBuilder()
                    .withTeamNames(teamNames)
                    .thenDescending(TeamStats::points)
                    .then(h2hRule)
                    .thenDescending(TeamStats::goalDiff)
                    .thenDescending(TeamStats::goalsFor)
                    .thenAscendingByName()
                    .build();
        }
    },

    /**
     * Head-to-head ranking with away goals rule.
     * Similar to HEAD_TO_HEAD, but away goals are prioritized in H2H comparisons.
     */
    HEAD_TO_HEAD_AWAY_GOALS {
        @Override
        public RankingRule build(List<Match> matches, List<Team> teams) {
            Objects.requireNonNull(matches, "Matches cannot be null for H2H-Away strategy");
            Map<UUID, String> teamNames = extractShortNames(teams);

            // H2H tiebreaker with away goals priority
            RankingRule h2hTieBreaker = new RankingBuilder()
                    .thenDescending(TeamStats::points)
                    .thenDescending(TeamStats::goalDiff)
                    .thenDescending(TeamStats::awayGoals) // Away goals rule
                    .build();

            // Performance optimization: use cached version
            RankingRule h2hRule = new CachedHeadToHeadRule(matches, h2hTieBreaker);

            return new RankingBuilder()
                    .withTeamNames(teamNames)
                    .thenDescending(TeamStats::points)
                    .then(h2hRule)
                    .thenDescending(TeamStats::goalDiff)
                    .thenDescending(TeamStats::goalsFor)
                    .thenAscendingByName()
                    .build();
        }
    },

    /**
     * English Premier League ranking rules (2024/25 season onwards).
     *
     * Official EPL tie-breaking rules:
     * 1. Points (most wins)
     * 2. Goal Difference (goals scored minus goals conceded)
     * 3. Goals For (total goals scored)
     * 4. Head-to-Head Points (in matches between tied teams)
     * 5. Head-to-Head Away Goals (away goals in matches between tied teams)
     * 6. Alphabetical order by team name
     *
     * This ensures no two teams occupy the same position.
     */
    ENGLISH_PREMIER_LEAGUE {
        @Override
        public RankingRule build(List<Match> matches, List<Team> teams) {
            Objects.requireNonNull(matches, "Matches cannot be null for EPL strategy");
            Map<UUID, String> teamNames = extractShortNames(teams);

            // Head-to-Head tie-breaker (EPL Rules C.17.1 and C.17.2)
            // First: H2H points, then: H2H away goals
            RankingRule h2hTieBreaker = new RankingBuilder()
                    .thenDescending(TeamStats::points) // C.17.1: H2H points
                    .thenDescending(TeamStats::awayGoals) // C.17.2: H2H away goals
                    .build();

            // Performance optimization: use cached version
            RankingRule h2hRule = new CachedHeadToHeadRule(matches, h2hTieBreaker);

            return new RankingBuilder()
                    .withTeamNames(teamNames)
                    .thenDescending(TeamStats::points) // C.4: Overall points
                    .thenDescending(TeamStats::goalDiff) // C.5: Goal difference
                    .thenDescending(TeamStats::goalsFor) // C.6: Goals scored
                    .then(h2hRule) // C.17: Head-to-head
                    .thenAscendingByName() // Final: Alphabetical
                    .build();
        }
    };

    /**
     * Builds a ranking rule for this strategy.
     *
     * @param matches All matches in the league (required for H2H strategies)
     * @param teams All teams in the league (required for alphabetical sorting)
     * @return The configured ranking rule
     * @throws NullPointerException if matches or teams is null (when required)
     */
    public abstract RankingRule build(List<Match> matches, List<Team> teams);

    /**
     * Extracts team short names for alphabetical comparison.
     */
    private static Map<UUID, String> extractShortNames(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            return Map.of();
        }
        return teams.stream().collect(Collectors.toMap(Team::getId, Team::getShortName));
    }
}
