package com.ligitabl.api.shared.errors;

public sealed interface UseCaseError
        permits AuthenticationError,
                AuthorizationError,
                ConflictError,
                NotFoundError,
                UnexpectedError,
                ValidationError {

    String getMessage();

    /** For logs only. May differ from {@link #getMessage()}, which is safe to show users. */
    default String logMessage() {
        return getMessage();
    }
}
