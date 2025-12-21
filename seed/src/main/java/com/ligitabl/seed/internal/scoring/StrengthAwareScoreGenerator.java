package com.ligitabl.seed.internal.scoring;

import java.util.Random;

public class StrengthAwareScoreGenerator {

    private static final double BASE_EXPECTED_GOALS = 1.75;
    private static final int MAX_GOALS = 6;
    private static final double HOME_ATTACK_BOOST = 1.15;
    private static final double HOME_DEFENSE_BOOST = 1.10;
    private static final double AWAY_ATTACK_PENALTY = 0.95;
    private static final double AWAY_DEFENSE_PENALTY = 0.90;

    private final Random random;

    public StrengthAwareScoreGenerator() {
        this.random = new Random();
    }

    public StrengthAwareScoreGenerator(long seed) {
        this.random = new Random(seed);
    }

    public MatchScore generateScore(TeamProfile home, TeamProfile away) {
        double homeExpected = calculateExpectedGoals(home, away, true);
        double awayExpected = calculateExpectedGoals(away, home, false);

        int homeGoals = Math.min(samplePoisson(homeExpected), MAX_GOALS);
        int awayGoals = Math.min(samplePoisson(awayExpected), MAX_GOALS);

        return new MatchScore(homeGoals, awayGoals);
    }

    private double calculateExpectedGoals(TeamProfile attacking, TeamProfile defending, boolean isHome) {
        double attackStrength = attacking.getAttackingStrength();
        double defenseStrength = defending.getDefensiveStrength();

        // Apply home/away modifiers
        attackStrength *= isHome ? HOME_ATTACK_BOOST : AWAY_ATTACK_PENALTY;
        defenseStrength *= isHome ? AWAY_DEFENSE_PENALTY : HOME_DEFENSE_BOOST;

        double expected = BASE_EXPECTED_GOALS * attackStrength * (1.0 - defenseStrength);
        return Math.max(0.1, expected);
    }

    private int samplePoisson(double lambda) {
        if (lambda <= 0) return 0;

        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;

        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);

        return k - 1;
    }
}
