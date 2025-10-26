package com.ligitabl.api.shared.errors;

// Represents a single validation failure, optionally tied to a field
public record ValidationMessage(String field, String message) {

    public ValidationMessage {
        if (field == null || field.isBlank()) {
            field = "general"; // fallback if not tied to a specific field
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Validation message cannot be blank");
        }
    }

    public static ValidationMessage of(String message) {
        return new ValidationMessage("general", message);
    }
}
