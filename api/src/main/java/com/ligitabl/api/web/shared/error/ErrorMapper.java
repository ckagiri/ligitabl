package com.ligitabl.api.web.shared.error;

import com.ligitabl.api.shared.errors.*;
import com.ligitabl.api.shared.errors.UseCaseError;

/**
 * Maps domain exceptions to use case errors.
 *
 * public Either<UseCaseError, SeasonPrediction> execute(Command cmd) {
 *     return Either.catching(
 *         () -> domainOperation(),
 *         ErrorMapper::toUseCaseError  // Convert exceptions to errors
 *     );
 * }
 */
public class ErrorMapper {

    public static int toHttpStatus(UseCaseError error) {
        if (error == null) {
            return 500;
        }
        if (error instanceof ValidationError) {
            return 400;
        }
        if (error instanceof NotFoundError) {
            return 404;
        }
        if (error instanceof ConflictError) {
            return 409;
        }
        if (error instanceof UnprocessableEntityError) {
            return 422;
        }
        if (error instanceof AuthenticationError) {
            return 401;
        }
        if (error instanceof AuthorizationError) {
            return 403;
        }
        return 500;
    }

    /**
     * Convert any exception to a UseCaseError.
     */
    public static UseCaseError toUseCaseError(Exception exception) {
        return switch (exception) {
                // Standard Java exceptions
            case IllegalArgumentException e -> UseCaseErrors.validation("Invalid input", e.getMessage());

            case IllegalStateException e -> UseCaseErrors.unprocessableEntity(
                    "Invalid state for operation: " + e.getMessage());

            case NullPointerException e -> UseCaseErrors.validation(
                    "Required field is missing", e.getMessage() != null ? e.getMessage() : "A required value is null");

                // Fallback for unknown exceptions
            default -> UseCaseErrors.unprocessableEntity(
                    exception.getMessage() != null
                            ? exception.getMessage()
                            : exception.getClass().getSimpleName());
        };
    }
}
