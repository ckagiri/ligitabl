package com.ligitabl.api.web.shared.dto;

import java.util.Objects;

/**
 * One team's view of one fixture.
 */
public record FixtureDto(
        String opponent, boolean isHome, String status, String result, Integer goalsFor, Integer goalsAgainst) {
    public FixtureDto {
        Objects.requireNonNull(opponent, "opponent is required");
        Objects.requireNonNull(status, "status is required");
    }

    /** Scoreless fixture — a match not yet played, or one whose score has not loaded. */
    public FixtureDto(String opponent, boolean isHome, String status, String result) {
        this(opponent, isHome, status, result, null, null);
    }

    /** True when both goal counts are present, i.e. the chip can render a score. */
    public boolean hasScore() {
        return goalsFor != null && goalsAgainst != null;
    }

    public static FixtureDto home(String opponent) {
        return new FixtureDto(opponent, true, "SCHEDULED", null);
    }

    public static FixtureDto away(String opponent) {
        return new FixtureDto(opponent, false, "SCHEDULED", null);
    }
}
