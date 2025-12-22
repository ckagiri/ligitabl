package com.ligitabl.model.auth;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentNotEmpty;

import java.util.regex.Pattern;

public record PublicId(String value) {

    private static final Pattern PUBLIC_ID_PATTERN =
            Pattern.compile("^[23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz]{10}$");

    public PublicId {
        if (value == null || value.length() != 10) {
            throw new IllegalArgumentException("PublicId must be exactly 10 characters");
        }
    }

    /**
     * Create a PublicId with validation.
     */
    public static PublicId create(String value) {
        assertArgumentNotEmpty(value, "Public id cannot be empty");

        if (!PUBLIC_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Public ID format: " + value);
        }

        return new PublicId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
