package com.ligitabl.model.domain;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentNotEmpty;
import static com.ligitabl.model.validator.AssertionUtils.assertArgumentTrue;

import java.util.Locale;

import com.ligitabl.model.shared.SlugValidator;

public record CompetitionSlug(String value) {
    public static CompetitionSlug of(String raw) {
        assertArgumentNotEmpty(raw, "Competition slug is required");

        String normalized = raw.toLowerCase(Locale.ROOT).trim();

        assertArgumentTrue(
                normalized.matches("[a-z0-9-]+"), "Competition slug must be lowercase alphanumeric with dashes");

        assertArgumentTrue(!normalized.matches("\\d+"), "Competition slug cannot be purely numeric");

        SlugValidator.assertNotUuid(normalized, "Competition slug");

        return new CompetitionSlug(normalized);
    }
}
