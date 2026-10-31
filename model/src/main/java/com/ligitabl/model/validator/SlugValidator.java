package com.ligitabl.model.validator;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentTrue;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SlugValidator implements ConstraintValidator<ValidSlug, String> {
    // Only lowercase letters, digits, and hyphens. No spaces, no uppercase.
    private static final String SLUG_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    public static void assertNotUuid(String value, String fieldName) {
        assertArgumentTrue(!value.matches(UUID_PATTERN), fieldName + " cannot be a UUID");
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // optional: null is allowed
        }
        if (value.isBlank()) {
            return false; // blank is not allowed
        }
        return value.matches(SLUG_PATTERN);
    }
}
