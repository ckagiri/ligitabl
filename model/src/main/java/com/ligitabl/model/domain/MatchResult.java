package com.ligitabl.model.domain;

import com.ligitabl.model.domain.standings.Constants;

/**
 * Represents the result of a football match with goals scored by each team.
 * Immutable and validated.
 *
 * @param homeGoals Goals scored by the home team
 * @param awayGoals Goals scored by the away team
 */
public record MatchResult(int homeGoals, int awayGoals) {
    public MatchResult {
        if (homeGoals < 0) {
            throw new IllegalArgumentException(String.format("Home goals cannot be negative: %d", homeGoals));
        }
        if (awayGoals < 0) {
            throw new IllegalArgumentException(String.format("Away goals cannot be negative: %d", awayGoals));
        }
        if (homeGoals > Constants.MAX_GOALS_PER_MATCH) {
            throw new IllegalArgumentException(
                    String.format("Home goals %d exceeds maximum %d", homeGoals, Constants.MAX_GOALS_PER_MATCH));
        }
        if (awayGoals > Constants.MAX_GOALS_PER_MATCH) {
            throw new IllegalArgumentException(
                    String.format("Away goals %d exceeds maximum %d", awayGoals, Constants.MAX_GOALS_PER_MATCH));
        }
    }

    public boolean isHomeWin() {
        return homeGoals > awayGoals;
    }

    public boolean isAwayWin() {
        return homeGoals < awayGoals;
    }

    public boolean isDraw() {
        return homeGoals == awayGoals;
    }

    public int goalDifference() {
        return homeGoals - awayGoals;
    }

    public int totalGoals() {
        return homeGoals + awayGoals;
    }

    @Override
    public String toString() {
        return String.format("%d-%d", homeGoals, awayGoals);
    }
}
