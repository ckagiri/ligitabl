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

    public static UnprocessableEntityError unprocessableEntity(String message) {
        return new UnprocessableEntityError(message);
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

    public static UseCaseError fromException(Throwable throwable) {
        if (throwable instanceof IllegalArgumentException) {
            return UseCaseErrors.unprocessableEntity(throwable.getMessage());
        } else if (throwable instanceof IllegalStateException) {
            return UseCaseErrors.conflict(throwable.getMessage());
        }
        return UseCaseErrors.unexpected(throwable);
    }
}
