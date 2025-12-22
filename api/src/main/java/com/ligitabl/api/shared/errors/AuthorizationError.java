package com.ligitabl.api.shared.errors;

public record AuthorizationError(String message) implements UseCaseError {
    @Override
    public String getMessage() {
        return message;
    }
}
