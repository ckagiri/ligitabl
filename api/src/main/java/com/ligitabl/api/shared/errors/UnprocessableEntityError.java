package com.ligitabl.api.shared.errors;

public record UnprocessableEntityError(String message) implements UseCaseError {
    @Override
    public String getMessage() {
        return message;
    }
}
