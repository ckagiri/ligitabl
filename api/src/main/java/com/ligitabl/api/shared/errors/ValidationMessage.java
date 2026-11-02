package com.ligitabl.api.shared.errors;

// Represents a single validation failure, optionally tied to a field
public record ValidationMessage(String field, String message) {
    private static final String GENERAL = "general";

    public ValidationMessage {
        if (field == null || field.isBlank()) {
            field = GENERAL; // fallback if not tied to a specific field
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Validation message cannot be blank");
        }
    }

    public static ValidationMessage of(String message) {
        return new ValidationMessage(GENERAL, message);
    }

    /** Safe to show users, so a message with no field stays as written. */
    public String display() {
        return GENERAL.equals(field) ? message : field + ": " + message;
    }
}
