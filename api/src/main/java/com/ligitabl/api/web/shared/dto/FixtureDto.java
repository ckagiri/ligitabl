package com.ligitabl.api.web.shared.dto;

import java.util.Objects;

public record FixtureDto(String opponent, boolean isHome) {
    public FixtureDto {
        Objects.requireNonNull(opponent, "opponent is required");
    }

    public static FixtureDto home(String opponent) {
        return new FixtureDto(opponent, true);
    }

    public static FixtureDto away(String opponent) {
        return new FixtureDto(opponent, false);
    }
}
