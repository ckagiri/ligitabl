package com.ligitabl.model.domain.standings.stats;

/**
 * Home or Away statistics for a team.
 * Reusable record for both home and away performance.
 */
public record HomeAwayStats(int played, int won, int drawn, int lost, int points) {

    public HomeAwayStats {
        if (played < 0) throw new IllegalArgumentException("Played cannot be negative");
        if (won < 0) throw new IllegalArgumentException("Won cannot be negative");
        if (drawn < 0) throw new IllegalArgumentException("Drawn cannot be negative");
        if (lost < 0) throw new IllegalArgumentException("Lost cannot be negative");
        if (points < 0) throw new IllegalArgumentException("Points cannot be negative");

        if (played != won + drawn + lost) {
            throw new IllegalArgumentException("Played must equal won + drawn + lost");
        }
    }

    public double winRate() {
        return played == 0 ? 0.0 : (double) won / played;
    }

    public double drawRate() {
        return played == 0 ? 0.0 : (double) drawn / played;
    }

    public double lossRate() {
        return played == 0 ? 0.0 : (double) lost / played;
    }

    public double pointsPerGame() {
        return played == 0 ? 0.0 : (double) points / played;
    }
}
