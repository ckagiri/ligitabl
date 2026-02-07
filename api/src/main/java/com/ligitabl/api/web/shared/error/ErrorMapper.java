package com.ligitabl.api.web.shared.error;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ligitabl.api.web.shared.domain.exception.InvalidSeasonPredictionException;
import com.ligitabl.api.web.shared.domain.exception.MultipleSwapException;
import com.ligitabl.api.web.shared.domain.exception.SeasonPredictionAlreadyExistsException;
import com.ligitabl.api.web.shared.domain.exception.SeasonPredictionNotFoundException;

/**
 * Maps domain exceptions to use case errors.
 *
 * <p>This is a critical component of the Railway-Oriented Programming pattern.
 * It sits at the boundary between the domain layer (which throws exceptions)
 * and the application layer (which uses Either monad).</p>
 *
 * <p>Usage pattern in use cases:</p>
 * <pre>
 * public Either<UseCaseError, SeasonPrediction> execute(Command cmd) {
 *     return Either.catching(
 *         () -> domainOperation(),
 *         ErrorMapper::toUseCaseError  // Convert exceptions to errors
 *     );
 * }
 * </pre>
 *
 * <p>This keeps the domain layer pure (using standard Java exceptions)
 * while providing Railway-Oriented error handling at the use case boundary.</p>
 */
public class ErrorMapper {

    /**
     * Convert any exception to a UseCaseError.
     *
     * <p>This method handles all domain exceptions and maps them to appropriate
     * use case error types. Unknown exceptions are mapped to BusinessRuleError.</p>
     *
     * @param exception the exception to map
     * @return the corresponding UseCaseError
     */
    public static UseCaseError toUseCaseError(Exception exception) {
        return switch (exception) {
                // Validation errors from domain
            case InvalidSeasonPredictionException e -> UseCaseError.ValidationError.of(
                    "Invalid season prediction", e.getMessage());

                // Business rule: Multiple swaps attempted
            case MultipleSwapException e -> UseCaseError.BusinessRuleError.of(
                    "Multiple swap attempt rejected", e.getMessage());

                // Not found errors
            case SeasonPredictionNotFoundException e -> UseCaseError.NotFoundError.of(
                    "SeasonPrediction", extractIdFromMessage(e.getMessage()));

                // Conflict errors (already exists)
            case SeasonPredictionAlreadyExistsException e -> UseCaseError.ConflictError.of(
                    "Season prediction already exists", e.getMessage());

                // Standard Java exceptions
            case IllegalArgumentException e -> UseCaseError.ValidationError.of("Invalid input", e.getMessage());

            case IllegalStateException e -> UseCaseError.BusinessRuleError.of(
                    "Invalid state for operation", e.getMessage());

            case NullPointerException e -> UseCaseError.ValidationError.of(
                    "Required field is missing", e.getMessage() != null ? e.getMessage() : "A required value is null");

                // Fallback for unknown exceptions
            default -> UseCaseError.BusinessRuleError.of(
                    "Operation failed",
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
        Pattern uuidPattern = java.util.regex.Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        Matcher matcher = uuidPattern.matcher(message);

        if (matcher.find()) {
            return matcher.group();
        }

        return "unknown";
    }

    /**
     * Map a validation exception with multiple errors.
     *
     * <p>Useful when domain validation produces multiple error messages.</p>
     *
     * @param message the main error message
     * @param details list of detailed error messages
     * @return a ValidationError with details
     */
    public static UseCaseError.ValidationError validationError(String message, java.util.List<String> details) {
        return new UseCaseError.ValidationError(message, details);
    }

    /**
     * Map a business rule violation.
     *
     * @param message the main error message
     * @param detail the rule that was violated
     * @return a BusinessRuleError
     */
    public static UseCaseError.BusinessRuleError businessRuleError(String message, String detail) {
        return UseCaseError.BusinessRuleError.of(message, detail);
    }

    /**
     * Map a not found error.
     *
     * @param resourceType the type of resource that wasn't found
     * @param resourceId the ID that wasn't found
     * @return a NotFoundError
     */
    public static UseCaseError.NotFoundError notFoundError(String resourceType, String resourceId) {
        return UseCaseError.NotFoundError.of(resourceType, resourceId);
    }

    /**
     * Map a conflict error.
     *
     * @param message the main error message
     * @param conflictReason the reason for the conflict
     * @return a ConflictError
     */
    public static UseCaseError.ConflictError conflictError(String message, String conflictReason) {
        return UseCaseError.ConflictError.of(message, conflictReason);
    }
}
