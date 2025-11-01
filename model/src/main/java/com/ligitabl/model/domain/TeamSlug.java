package com.ligitabl.model.domain;

import static com.ligitabl.model.validator.AssertionUtils.*;

import java.util.Locale;

public record TeamSlug(String value) {
    public static TeamSlug of(String raw) {
        assertArgumentNotEmpty(raw, "Slug is required");

        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        assertArgumentTrue(normalized.matches("[a-z0-9-]+"), "Slug must be lowercase alphanumeric with dashes");

        return new TeamSlug(normalized);
    }
}
