package com.ligitabl.seed.internal.scoring;

public class MatchScore {
    private final int homeGoals;
    private final int awayGoals;

    public MatchScore(int homeGoals, int awayGoals) {
        this.homeGoals = Math.max(0, homeGoals);
        this.awayGoals = Math.max(0, awayGoals);
    }

    public int getHomeGoals() { return homeGoals; }
    public int getAwayGoals() { return awayGoals; }

    public String format() {
        return homeGoals + "-" + awayGoals;
    }
}
