package com.ligitabl.model.domain.standings;

import java.util.regex.Pattern;

/**
 * Centralized constants for the soccer standings system.
 * Single source of truth for game rules and patterns.
 */
public final class Constants {
    private Constants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // Scoring rules
    public static final int POINTS_FOR_WIN = 3;
    public static final int POINTS_FOR_DRAW = 1;
    public static final int POINTS_FOR_LOSS = 0;

    // Validation patterns
    public static final Pattern SCORE_PATTERN = Pattern.compile("^(\\d+)-(\\d+)$");
    public static final int MAX_GOALS_PER_MATCH = 50;

    // Cache configuration
    public static final int MAX_CACHE_SIZE = 1000;
}
