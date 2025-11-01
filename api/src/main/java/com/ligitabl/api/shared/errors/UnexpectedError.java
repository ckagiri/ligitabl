package com.ligitabl.api.shared.errors;

import java.util.Optional;

public record UnexpectedError(Throwable cause) implements UseCaseError {
    @Override
    public String getMessage() {
        return Optional.ofNullable(cause)
                .map(Throwable::getMessage)
                .filter(msg -> !msg.isBlank())
                .orElse("An unexpected error occurred");
    }
}
