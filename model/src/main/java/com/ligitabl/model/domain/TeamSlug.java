package com.ligitabl.model.domain;

import java.util.Locale;

import com.ligitabl.model.shared.Either;

public record TeamSlug(String value) {
    public static Either<SlugError, TeamSlug> of(String raw) {
        if (raw == null || raw.isBlank()) {
            return Either.left(new SlugError.Blank());
        }
        String normalized = raw.toLowerCase(Locale.ROOT).trim();

        // Example: enforce only [a-z0-9-]
        if (!normalized.matches("[a-z0-9-]+")) {
            return Either.left(new SlugError.InvalidFormat("Slug must be lowercase alphanumeric with dashes"));
        }

        return Either.right(new TeamSlug(normalized));
    }
}
