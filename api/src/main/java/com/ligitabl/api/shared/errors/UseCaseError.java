package com.ligitabl.api.shared.errors;

public sealed interface UseCaseError extends DomainError
        permits AuthenticationError,
                AuthorizationError,
                ConflictError,
                NotFoundError,
                UnexpectedError,
                UnprocessableEntityError,
                ValidationError {

    /** For logs only. May differ from {@link #getMessage()}, which is safe to show users. */
    default String logMessage() {
        return getMessage();
    }
}
