package com.ligitabl.model.domain.standings.stats;

import java.util.Objects;
import java.util.UUID;

import com.ligitabl.model.domain.standings.table.TeamMatchView;

public record TeamStats(
        UUID teamId,
        int played,
        int won,
        int drawn,
        int lost,
        int goalsFor,
        int goalsAgainst,
        int awayGoals,
        int homePlayed,
        int awayPlayed,
        int homePoints,
        int awayPoints) {
    public TeamStats {
        Objects.requireNonNull(teamId, "Team ID cannot be null");
        if (played < 0) throw new IllegalArgumentException("Played cannot be negative");
        if (won < 0) throw new IllegalArgumentException("Won cannot be negative");
        if (drawn < 0) throw new IllegalArgumentException("Drawn cannot be negative");
        if (lost < 0) throw new IllegalArgumentException("Lost cannot be negative");
        if (goalsFor < 0) throw new IllegalArgumentException("Goals for cannot be negative");
        if (goalsAgainst < 0) throw new IllegalArgumentException("Goals against cannot be negative");
        if (awayGoals < 0) throw new IllegalArgumentException("Away goals cannot be negative");
        if (homePlayed < 0) throw new IllegalArgumentException("Home played cannot be negative");
        if (awayPlayed < 0) throw new IllegalArgumentException("Away played cannot be negative");
        if (homePoints < 0) throw new IllegalArgumentException("Home points cannot be negative");
        if (awayPoints < 0) throw new IllegalArgumentException("Away points cannot be negative");
        if (played != won + drawn + lost) throw new IllegalArgumentException("Played must equal won+drawn+lost");
        if (played != homePlayed + awayPlayed) throw new IllegalArgumentException("Played must equal home+away");
        if (awayGoals > goalsFor) throw new IllegalArgumentException("Away goals cannot exceed goals for");
    }

    public static TeamStats empty(UUID teamId) {
        return new TeamStats(teamId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public TeamStats withMatch(TeamMatchView view) {
        Objects.requireNonNull(view);

        if (!view.teamId().equals(this.teamId)) {
            throw new IllegalArgumentException(
                    String.format("Match view is for team %s but stats are for team %s", view.teamId(), this.teamId()));
        }

        return new TeamStats(
                teamId,
                played + 1,
                won + (view.won() ? 1 : 0),
                drawn + (view.drew() ? 1 : 0),
                lost + (view.lost() ? 1 : 0),
                goalsFor + view.goalsFor(),
                goalsAgainst + view.goalsAgainst(),
                awayGoals + (view.wasHome() ? 0 : view.goalsFor()),
                homePlayed + (view.wasHome() ? 1 : 0),
                awayPlayed + (view.wasHome() ? 0 : 1),
                homePoints + (view.wasHome() ? view.points() : 0),
                awayPoints + (view.wasHome() ? 0 : view.points()));
    }

    /**
     * @return Total points (3 per win, 1 per draw)
     */
    public int points() {
        return won * 3 + drawn;
    }

    /**
     * @return Goal difference (goals for minus goals against)
     */
    public int goalDiff() {
        return goalsFor - goalsAgainst;
    }

    /**
     * @return Goals scored at home
     */
    public int homeGoals() {
        return goalsFor - awayGoals;
    }
    /**
     * @return Points per game ratio
     */
    public double pointsPerGame() {
        return played == 0 ? 0.0 : (double) points() / played;
    }

    /**
     * @return Win rate as a percentage (0.0 to 1.0)
     */
    public double winRate() {
        return played == 0 ? 0.0 : (double) won / played;
    }

    /**
     * @return Draw rate as a percentage (0.0 to 1.0)
     */
    public double drawRate() {
        return played == 0 ? 0.0 : (double) drawn / played;
    }

    /**
     * @return Loss rate as a percentage (0.0 to 1.0)
     */
    public double lossRate() {
        return played == 0 ? 0.0 : (double) lost / played;
    }

    /**
     * @return Average goals scored per game
     */
    public double goalsForPerGame() {
        return played == 0 ? 0.0 : (double) goalsFor / played;
    }

    /**
     * @return Average goals conceded per game
     */
    public double goalsAgainstPerGame() {
        return played == 0 ? 0.0 : (double) goalsAgainst / played;
    }

    /**
     * @return Average goal difference per game
     */
    public double goalDiffPerGame() {
        return played == 0 ? 0.0 : (double) goalDiff() / played;
    }

    /**
     * @return Points per game at home
     */
    public double homePointsPerGame() {
        return homePlayed == 0 ? 0.0 : (double) homePoints / homePlayed;
    }

    /**
     * @return Points per game away
     */
    public double awayPointsPerGame() {
        return awayPlayed == 0 ? 0.0 : (double) awayPoints / awayPlayed;
    }

    /**
     * @return Win-Draw-Loss record as a string (e.g., "10-5-3")
     */
    public String formattedRecord() {
        return String.format("%d-%d-%d", won, drawn, lost);
    }

    /**
     * @return true if the team has played at least one match
     */
    public boolean hasPlayed() {
        return played > 0;
    }

    /**
     * @return true if the team has a positive goal difference
     */
    public boolean isPositiveGoalDiff() {
        return goalDiff() > 0;
    }

    /**
     * @return Number of clean sheets (matches with no goals conceded)
     * Note: This is an approximation as we don't track per-match clean sheets
     */
    public boolean hasCleanSheetPotential() {
        return goalsAgainst < played; // If average < 1, at least one clean sheet likely
    }

    @Override
    public String toString() {
        return String.format(
                "TeamStats[%s: P%d W%d D%d L%d F%d A%d GD%+d Pts%d]",
                teamId, played, won, drawn, lost, goalsFor, goalsAgainst, goalDiff(), points());
    }
}
