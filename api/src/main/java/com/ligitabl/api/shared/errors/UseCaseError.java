package com.ligitabl.api.shared.errors;

public sealed interface UseCaseError extends DomainError
    permits NotFoundError, ValidationError, UnexpectedError {
}
