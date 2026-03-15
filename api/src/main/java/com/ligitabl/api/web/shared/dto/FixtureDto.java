package com.ligitabl.api.web.shared.dto;

import java.util.Objects;

public record FixtureDto(String opponent, boolean isHome, String status, String result) {
    public FixtureDto {
        Objects.requireNonNull(opponent, "opponent is required");
        Objects.requireNonNull(status, "status is required");
    }

    public static FixtureDto home(String opponent) {
        return new FixtureDto(opponent, true, "SCHEDULED", null);
    }

    public static FixtureDto away(String opponent) {
        return new FixtureDto(opponent, false, "SCHEDULED", null);
    }
}
