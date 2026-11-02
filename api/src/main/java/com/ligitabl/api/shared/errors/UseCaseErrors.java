package com.ligitabl.api.shared.errors;

import com.ligitabl.model.shared.InvalidValueException;
import com.ligitabl.model.shared.RuleViolationException;

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

    public static UnexpectedError unexpected(Throwable exception) {
        return new UnexpectedError(exception);
    }

    public static UnexpectedError unexpected(String message) {
        return new UnexpectedError(new RuntimeException(message));
    }

    public static AuthenticationError unauthenticated(String message) {
        return new AuthenticationError(message);
    }

    public static UseCaseError fromException(Throwable throwable) {
        return switch (throwable) {
            case InvalidValueException e -> validation(e.getMessage());
            case RuleViolationException e -> conflict(e.getMessage());
            default -> unexpected(throwable);
        };
    }
}
