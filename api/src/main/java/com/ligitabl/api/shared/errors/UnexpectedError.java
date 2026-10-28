package com.ligitabl.api.shared.errors;

import java.util.Optional;

public record UnexpectedError(Throwable cause) implements UseCaseError {
    public static final String PUBLIC_MESSAGE = "An unexpected error occurred";

    /** Never the cause's message: callers render {@code getMessage()} straight into responses. */
    @Override
    public String getMessage() {
        return PUBLIC_MESSAGE;
    }

    @Override
    public String logMessage() {
        return Optional.ofNullable(cause)
                .map(Throwable::getMessage)
                .filter(msg -> !msg.isBlank())
                .orElse(PUBLIC_MESSAGE);
    }
}
