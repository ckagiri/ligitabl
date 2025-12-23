package com.ligitabl.seed.internal;

import static com.ligitabl.seed.internal.util.SeedCoercions.asString;

import java.util.Map;

public record DefaultsConfig(String competitionSlug, String seasonSlug) {

    public static DefaultsConfig fromMap(Map<String, Object> defaults) {
        if (defaults == null) {
            return null;
        }
        String competitionSlug = asString(defaults.get("competitionSlug"));
        String seasonSlug = asString(defaults.get("seasonSlug"));
        return new DefaultsConfig(competitionSlug, seasonSlug);
    }

    public void validateRequired() {
        if (competitionSlug == null || competitionSlug.isBlank()) {
            throw new IllegalStateException("Missing required defaults.competitionSlug (defaults.yaml)");
        }
        if (seasonSlug == null || seasonSlug.isBlank()) {
            throw new IllegalStateException("Missing required defaults.seasonSlug (defaults.yaml)");
        }
    }
}
