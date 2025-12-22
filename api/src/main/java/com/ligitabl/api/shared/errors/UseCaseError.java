package com.ligitabl.api.shared.errors;

public sealed interface UseCaseError extends DomainError
        permits AuthenticationError,
                AuthorizationError,
                ConflictError,
                NotFoundError,
                UnexpectedError,
                UnprocessableEntityError,
                ValidationError {}
