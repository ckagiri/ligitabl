package com.ligitabl.model.auth;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentNotEmpty;

import java.util.Arrays;

public enum Role {
    ADMIN("ADMIN"),
    PLAYER("PLAYER"),
    MODERATOR("MODERATOR");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Role fromString(String value) {
        assertArgumentNotEmpty(value, "Role cannot be empty");

        return Arrays.stream(Role.values())
                .filter(role -> role.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid role: " + value));
    }

    @Override
    public String toString() {
        return value;
    }
}
