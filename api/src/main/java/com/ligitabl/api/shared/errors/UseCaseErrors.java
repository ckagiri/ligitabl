package com.ligitabl.api.shared.errors;

public final class UseCaseErrors {
    private UseCaseErrors() {
        // prevent instantiation
    }

    public static ValidationError validation(String message) {
        return new ValidationError(message);
    }

    public static ValidationError validation(String field, String message) {
        return new ValidationError(new ValidationMessage(field, message));
    }

    public static ConflictError conflict(String message) {
        return new ConflictError(message);
    }

    public static NotFoundError notFound(String message) {
        return new NotFoundError(message);
    }

    public static NotFoundError notFound(String entity, Object identifier) {
        return new NotFoundError(entity, identifier);
    }

    public static NotFoundError notFound(String entity, String field, Object identifier) {
        return new NotFoundError(entity, field, identifier);
    }
}
