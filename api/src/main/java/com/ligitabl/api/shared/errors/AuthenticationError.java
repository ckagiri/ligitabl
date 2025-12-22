package com.ligitabl.api.shared.errors;

public record AuthenticationError(String message) implements UseCaseError {
    @Override
    public String getMessage() {
        return message;
    }
}
