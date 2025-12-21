package com.ligitabl.model.domain.standings.ranking;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Team;

/**
 * Pre-defined ranking strategies for different soccer competitions.
 */
public enum RankingStrategy {

    /**
     * Standard ranking: Points → Goal Difference → Goals For
     * Allows tied positions.
     */
    STANDARD {
        @Override
        public RankingRule build(List<Match> matches, List<Team> teams) {
            return new RankingBuilder()
                    .withTeams(teams)
                    .byMostPoints()
                    .byBestGoalDifference()
                    .byMostGoalsScored()
                    .byAlphabetical()
                    .build();
        }
    },

    /**
     * Head-to-head ranking: Points → H2H → Goal Difference → Goals For
     * Used in La Liga, Serie A, and other European leagues.
     * Allows tied positions if all criteria equal.
     */
    HEAD_TO_HEAD {
        @Override
        public RankingRule build(List<Match> matches, List<Team> teams) {
            return new RankingBuilder()
                    .withMatches(matches)
                    .withTeams(teams)
                    .byMostPoints()
                    .byHeadToHead()
                    .byBestGoalDifference()
                    .byMostGoalsScored()
                    .build();
        }
    },

    /**
     * Head-to-head with away goals: Points → H2H (with away goals) → Goal Difference → Goals For
     * Allows tied positions if all criteria equal.
     */
    HEAD_TO_HEAD_AWAY_GOALS {
        @Override
        public RankingRule build(List<Match> matches, List<Team> teams) {
            return new RankingBuilder()
                    .withMatches(matches)
                    .withTeams(teams)
                    .byMostPoints()
                    .byHeadToHeadAwayGoals()
                    .byBestGoalDifference()
                    .byMostGoalsScored()
                    .build();
        }
    },

    /**
     * English Premier League official ranking rules.
     *
     * Official Rules (Premier League Handbook):
     * - C.4: Teams ranked by points
     * - C.5: If equal, by goal difference
     * - C.6: If still equal, by goals scored
     * - C.17.1: If still equal, by head-to-head points
     * - C.17.2: If still equal, by head-to-head away goals
     * - Final: Alphabetical order (ensures no tied positions)
     *
     * KEY DIFFERENCE: EPL checks GD and GF BEFORE head-to-head,
     * unlike other H2H strategies which apply H2H immediately after points.
     */
    ENGLISH_PREMIER_LEAGUE {
        @Override
        public RankingRule build(List<Match> matches, List<Team> teams) {
            Map<UUID, String> shortNames = extractShortNames(teams);

            // H2H tie-breaker
            RankingRule h2hTieBreaker =
                    new RankingBuilder().byMostPoints().byMostAwayGoals().build();

            RankingRule h2hRule = new CachedHeadToHeadRule(matches, h2hTieBreaker);

            return new RankingBuilder()
                    .withTeams(teams)
                    .byMostPoints()
                    .byBestGoalDifference()
                    .byMostGoalsScored()
                    .then(h2hRule)
                    .byAlphabetical()
                    .build();
        }
    };

    /**
     * Build the ranking rule for this strategy.
     */
    public abstract RankingRule build(List<Match> matches, List<Team> teams);

    protected static Map<UUID, String> extractShortNames(List<Team> teams) {
        return teams.stream().collect(Collectors.toMap(Team::getId, Team::getShortName));
    }
}
