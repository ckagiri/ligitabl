package com.ligitabl.model.domain.standings;

public final class Constants {
    private Constants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    /**
     * Regular expression for validating score format (e.g., "2-1", "0-0", "10-5")
     */
    public static final String SCORE_REGEX = "^(\\d+)-(\\d+)$";

    /**
     * Header for the standings table display
     */
    public static final String STANDINGS_HEADER =
            """
        Pos  Team             Pld    W    D    L   GF   GA   GD  Pts
        ---  ---------------  ---  ---  ---  ---  ---  ---  ---  ---""";

    /**
     * Maximum allowed goals in a single match (sanity check)
     */
    public static final int MAX_GOALS_PER_MATCH = 50;
}
