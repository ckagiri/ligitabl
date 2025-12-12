package com.ligitabl.model.shared;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentTrue;

public class SlugValidator {
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    public static void assertNotUuid(String value, String fieldName) {
        assertArgumentTrue(!value.matches(UUID_PATTERN), fieldName + " cannot be a UUID");
    }
}
