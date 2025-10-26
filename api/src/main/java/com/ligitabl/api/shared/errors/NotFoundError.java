package com.ligitabl.api.shared.errors;

public record NotFoundError(String entity, Object identifier) implements UseCaseError {
    @Override
    public String getMessage() {
        return entity + " with id " + identifier + " was not found.";
    }
}
