package com.ligitabl.api.web.shared.dto.response;

import java.util.Objects;

public record FixtureDto(
        String opponent,
        boolean isHome
) {
    public FixtureDto {
        Objects.requireNonNull(opponent, "opponent is required");
    }

    /**
     * Factory method for home fixture.
     */
    public static FixtureDto home(String opponent) {
        return new FixtureDto(opponent, true);
    }

    /**
     * Factory method for away fixture.
     */
    public static FixtureDto away(String opponent) {
        return new FixtureDto(opponent, false);
    }

    /**
     * Get display string like "vs ARS (H)" or "vs LIV (A)".
     */
    public String getDisplayString() {
        return "vs " + opponent + " (" + (isHome ? "H" : "A") + ")";
    }
}
