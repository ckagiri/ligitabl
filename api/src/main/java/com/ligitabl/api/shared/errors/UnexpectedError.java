package com.ligitabl.api.shared.errors;

public record UnexpectedError(Exception cause) implements UseCaseError {
    @Override
    public String getMessage() {
        return "An unexpected error occurred.";
    }
}
