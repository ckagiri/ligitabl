package com.ligitabl.model.domain.standings.stats;

import java.util.Objects;
import java.util.UUID;

import com.ligitabl.model.domain.TeamMatchView;
import com.ligitabl.model.domain.standings.Constants;

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
        int homeWon,
        int homeDrawn,
        int homeLost,
        int homePoints,
        int awayPlayed,
        int awayWon,
        int awayDrawn,
        int awayLost,
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

        if (played != won + drawn + lost) {
            throw new IllegalArgumentException("Played must equal won + drawn + lost");
        }
        if (played != homePlayed + awayPlayed) {
            throw new IllegalArgumentException("Played must equal home + away");
        }
    }

    public int points() {
        return won * Constants.POINTS_FOR_WIN + drawn * Constants.POINTS_FOR_DRAW;
    }

    public int goalDiff() {
        return goalsFor - goalsAgainst;
    }

    public int homeGoals() {
        return goalsFor - awayGoals;
    }

    public HomeAwayStats homeStats() {
        return new HomeAwayStats(homePlayed, homeWon, homeDrawn, homeLost, homePoints);
    }

    public HomeAwayStats awayStats() {
        return new HomeAwayStats(awayPlayed, awayWon, awayDrawn, awayLost, awayPoints);
    }

    public double pointsPerGame() {
        return played == 0 ? 0.0 : (double) points() / played;
    }

    public double winRate() {
        return played == 0 ? 0.0 : (double) won / played;
    }

    public double goalsForPerGame() {
        return played == 0 ? 0.0 : (double) goalsFor / played;
    }

    public double goalsAgainstPerGame() {
        return played == 0 ? 0.0 : (double) goalsAgainst / played;
    }

    public String formattedRecord() {
        return String.format("%d-%d-%d", won, drawn, lost);
    }

    public static TeamStats empty(UUID teamId) {
        return new TeamStats(teamId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public TeamStats withMatch(TeamMatchView view) {
        Objects.requireNonNull(view);

        if (!view.teamId().equals(this.teamId)) {
            throw new IllegalArgumentException("Match view is for different team");
        }

        boolean won = view.won();
        boolean drew = view.drew();
        boolean home = view.wasHome();
        int matchPoints = view.points();

        return new TeamStats(
                teamId,
                played + 1,
                this.won + (won ? 1 : 0),
                drawn + (drew ? 1 : 0),
                lost + (view.lost() ? 1 : 0),
                goalsFor + view.goalsFor(),
                goalsAgainst + view.goalsAgainst(),
                awayGoals + (home ? 0 : view.goalsFor()),
                homePlayed + (home ? 1 : 0),
                homeWon + (home && won ? 1 : 0),
                homeDrawn + (home && drew ? 1 : 0),
                homeLost + (home && view.lost() ? 1 : 0),
                homePoints + (home ? matchPoints : 0),
                awayPlayed + (!home ? 1 : 0),
                awayWon + (!home && won ? 1 : 0),
                awayDrawn + (!home && drew ? 1 : 0),
                awayLost + (!home && view.lost() ? 1 : 0),
                awayPoints + (!home ? matchPoints : 0));
    }
}
