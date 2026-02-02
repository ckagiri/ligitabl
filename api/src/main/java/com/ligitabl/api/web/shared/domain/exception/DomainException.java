package com.ligitabl.api.web.shared.domain.exception;

/**
 * Base exception for all domain-related exceptions.
 * Domain exceptions represent business rule violations and invalid states.
 *
 * These exceptions are caught at the use case boundary and wrapped in Either<UseCaseError, T>.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
