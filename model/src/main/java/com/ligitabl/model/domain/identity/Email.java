package com.ligitabl.model.domain.identity;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentNotEmpty;

import java.util.regex.Pattern;

import com.ligitabl.model.shared.InvalidValueException;

public record Email(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        assertArgumentNotEmpty("Email cannot be empty", value);
    }

    public static Email create(String value) {
        assertArgumentNotEmpty(value, "Email cannot be empty");

        String normalized = value.trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidValueException("Invalid email address");
        }

        return new Email(normalized);
    }

    @Override
    public String toString() {
        return value;
    }
}
