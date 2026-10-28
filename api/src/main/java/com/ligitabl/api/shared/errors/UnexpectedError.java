package com.ligitabl.api.shared.errors;

import java.util.Optional;

public record UnexpectedError(Throwable cause) implements UseCaseError {
    /** What a response may show. {@link #getMessage()} is the cause's message and belongs in logs only. */
    public static final String PUBLIC_MESSAGE = "An unexpected error occurred";

    @Override
    public String getMessage() {
        return Optional.ofNullable(cause)
                .map(Throwable::getMessage)
                .filter(msg -> !msg.isBlank())
                .orElse(PUBLIC_MESSAGE);
    }
}
