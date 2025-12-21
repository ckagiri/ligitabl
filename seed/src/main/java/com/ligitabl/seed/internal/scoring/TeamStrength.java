package com.ligitabl.seed.internal.scoring;

public enum TeamStrength {
    ELITE(85, 100),
    STRONG(70, 84),
    MEDIUM(55, 69),
    WEAK(40, 54),
    RELEGATION(0, 39);

    private final int minRating;
    private final int maxRating;

    TeamStrength(int minRating, int maxRating) {
        this.minRating = minRating;
        this.maxRating = maxRating;
    }

    public int getMinRating() {
        return minRating;
    }

    public int getMaxRating() {
        return maxRating;
    }

    public boolean isValidRating(int rating) {
        return rating >= minRating && rating <= maxRating;
    }

    public static TeamStrength fromRating(int rating) {
        for (TeamStrength strength : values()) {
            if (strength.isValidRating(rating)) {
                return strength;
            }
        }
        return MEDIUM; // Default fallback
    }
}
