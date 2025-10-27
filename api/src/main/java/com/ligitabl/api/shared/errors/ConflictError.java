package com.ligitabl.api.shared.errors;

public record ConflictError(String message) implements UseCaseError {
    @Override
    public String getMessage() {
        return message;
    }
}
