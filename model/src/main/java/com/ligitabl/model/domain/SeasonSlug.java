package com.ligitabl.model.domain;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentNotEmpty;
import static com.ligitabl.model.validator.AssertionUtils.assertArgumentTrue;

import java.util.Locale;

import com.ligitabl.model.shared.SlugValidator;

public record SeasonSlug(String value) {
    public static SeasonSlug of(String raw) {
        assertArgumentNotEmpty(raw, "Season slug is required");

        String normalized = raw.toLowerCase(Locale.ROOT).trim();

        assertArgumentTrue(
                normalized.matches("\\d{4}-\\d{2}"), "Season slug must be in format YYYY-YY (e.g., 2024-25)");

        SlugValidator.assertNotUuid(normalized, "Season slug");

        return new SeasonSlug(normalized);
    }

    public static SeasonSlug fromShorthand(String shorthand) {
        assertArgumentNotEmpty(shorthand, "Season shorthand is required");

        String trimmed = shorthand.trim();
        assertArgumentTrue(trimmed.matches("\\d{4}"), "Season shorthand must be 4 digits (e.g., 2526)");

        return SeasonSlug.of("20" + trimmed.substring(0, 2) + "-" + trimmed.substring(2));
    }

    public String toShorthand() {
        return value.substring(2, 4) + value.substring(5);
    }
}
