package com.ligitabl.seed.internal;

import static com.ligitabl.seed.internal.util.SeedCoercions.asString;

import java.util.Map;

public record DefaultsConfig(String competitionSlug) {

    public static DefaultsConfig fromMap(Map<String, Object> defaults) {
        if (defaults == null) {
            return null;
        }
        String competitionSlug = asString(defaults.get("competitionSlug"));
        return new DefaultsConfig(competitionSlug);
    }

    public void validateRequired() {
        if (competitionSlug == null || competitionSlug.isBlank()) {
            throw new IllegalStateException("Missing required defaults.competitionSlug (defaults.yaml)");
        }
    }
}
