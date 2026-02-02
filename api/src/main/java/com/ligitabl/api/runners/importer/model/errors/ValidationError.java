package com.ligitabl.api.runners.importer.model.errors;

import lombok.Value;

@Value
public class ValidationError implements ImportError {
    String message;
    String code;
    String field;

    @Override
    public String message() {
        return message;
    }

    @Override
    public String code() {
        return code;
    }

    public static ValidationError of(String message, String field) {
        return new ValidationError(message, "VALIDATION_ERROR", field);
    }

    public static ValidationError missingField(String field) {
        return new ValidationError("Required field is missing: " + field, "MISSING_FIELD", field);
    }

    public static ValidationError invalidData(String field, String reason) {
        return new ValidationError(
                String.format("Invalid data in field '%s': %s", field, reason), "INVALID_DATA", field);
    }
}
