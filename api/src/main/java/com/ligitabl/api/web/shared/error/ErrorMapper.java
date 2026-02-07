package com.ligitabl.api.web.shared.error;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ligitabl.api.shared.errors.*;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.domain.exception.InvalidSeasonPredictionException;
import com.ligitabl.api.web.shared.domain.exception.MultipleSwapException;
import com.ligitabl.api.web.shared.domain.exception.SeasonPredictionAlreadyExistsException;
import com.ligitabl.api.web.shared.domain.exception.SeasonPredictionNotFoundException;

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
     *
     * This method handles all domain exceptions and maps them to appropriate
     * use case error types. Unknown exceptions are mapped to BusinessRuleError.</p>
     */
    public static UseCaseError toUseCaseError(Exception exception) {
        return switch (exception) {
                // Validation errors from domain
            case InvalidSeasonPredictionException e -> new ValidationError(
                    List.of(ValidationMessage.of("Invalid season prediction")));

                // Business rule: Multiple swaps attempted
            case MultipleSwapException e -> new UnprocessableEntityError(
                    "Multiple swap attempt rejected: " + e.getMessage());

                // Not found errors
            case SeasonPredictionNotFoundException e -> new NotFoundError(
                    "SeasonPrediction", "id", extractIdFromMessage(e.getMessage()));

                // Conflict errors (already exists)
            case SeasonPredictionAlreadyExistsException e -> UseCaseErrors.conflict(
                    "Season prediction already exists: " + e.getMessage());

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

    /**
     * Try to extract an ID from an exception message.
     *
     * <p>This is a best-effort attempt. If no ID can be extracted, returns "unknown".</p>
     *
     * @param message the exception message
     * @return extracted ID or "unknown"
     */
    private static String extractIdFromMessage(String message) {
        if (message == null) {
            return "unknown";
        }

        // Try to extract UUID pattern
        // Pattern: 8-4-4-4-12 hexadecimal digits
        Pattern uuidPattern =
                Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        Matcher matcher = uuidPattern.matcher(message);

        if (matcher.find()) {
            return matcher.group();
        }

        return "unknown";
    }
}
