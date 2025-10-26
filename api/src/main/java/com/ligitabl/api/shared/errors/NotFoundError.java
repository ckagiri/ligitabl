package com.ligitabl.api.shared.errors;

public record NotFoundError(String entity, String field, Object identifier, String message)
    implements UseCaseError {

    public NotFoundError(String entity, Object identifier) {
        this(entity, "id", identifier, entity + " with id " + identifier + " was not found.");
    }

    public NotFoundError(String entity, String field, Object identifier) {
        this(entity, field, identifier,
            entity + " with " + field + " " + identifier + " was not found.");
    }

    public NotFoundError(String message) {
        this(null, null, null, message);
    }

    @Override
    public String getMessage() {
        return message;
    }
}
