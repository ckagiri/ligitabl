package com.ligitabl.model.domain;

import com.ligitabl.model.shared.SlugValidator;

import java.util.Locale;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentNotEmpty;
import static com.ligitabl.model.validator.AssertionUtils.assertArgumentTrue;

public record SeasonSlug(String value) {
    public static SeasonSlug of(String raw) {
        assertArgumentNotEmpty(raw, "Season slug is required");

        String normalized = raw.toLowerCase(Locale.ROOT).trim();

        assertArgumentTrue(
                normalized.matches("\\d{4}-\\d{2}"),
                "Season slug must be in format YYYY-YY (e.g., 2024-25)"
        );

        SlugValidator.assertNotUuid(normalized, "Season slug");

        return new SeasonSlug(normalized);
    }
}
