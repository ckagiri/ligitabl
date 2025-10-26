package com.ligitabl.model.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SlugValidator implements ConstraintValidator<ValidSlug, String> {

    private static final String SLUG_PATTERN = "^[a-z0-9-]+$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // optional: null is allowed
        }
        if (value.isBlank()) {
            return false; // blank is not allowed
        }
        String normalized = value.toLowerCase().trim();
        return normalized.matches(SLUG_PATTERN);
    }
}

