package com.ligitabl.api.web.shared.error;

import java.util.List;

/**
 * Sealed interface representing all possible use case errors.
 *
 * <p>This is the "Left" side of the Railway-Oriented Programming pattern.
 * Use cases return {@code Either<UseCaseError, Result>} where Left represents failure.</p>
 *
 * <p>Being a sealed interface ensures exhaustive pattern matching when handling errors.</p>
 *
 * <p>Example usage in controller:</p>
 * <pre>
 * Either<UseCaseError, SeasonPrediction> result = useCase.execute(command);
 *
 * return result.fold(
 *     error -> switch (error) {
 *         case ValidationError e -> handleValidationError(e);
 *         case BusinessRuleError e -> handleBusinessRuleError(e);
 *         case NotFoundError e -> handleNotFoundError(e);
 *         case ConflictError e -> handleConflictError(e);
 *     },
 *     success -> handleSuccess(success)
 * );
 * </pre>
 */
public sealed interface UseCaseError
        permits UseCaseError.ValidationError,
                UseCaseError.BusinessRuleError,
                UseCaseError.NotFoundError,
                UseCaseError.ConflictError {

    /**
     * Get the error message.
     *
     * @return the main error message
     */
    String message();

    /**
     * Get additional error details.
     *
     * @return list of detailed error messages (may be empty)
     */
    List<String> details();

    /**
     * Get the error type for categorization.
     *
     * @return the error type enum
     */
    ErrorType type();

    /**
     * Enum categorizing error types for HTTP status code mapping.
     */
    enum ErrorType {
        /** Validation errors (HTTP 400 Bad Request) */
        VALIDATION,

        /** Business rule violations (HTTP 400 Bad Request) */
        BUSINESS_RULE,

        /** Resource not found (HTTP 404 Not Found) */
        NOT_FOUND,

        /** Resource already exists or conflict (HTTP 409 Conflict) */
        CONFLICT
    }

    /**
     * Validation error - invalid input data.
     *
     * <p>Maps to HTTP 400 Bad Request.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Invalid team code</li>
     *   <li>Invalid position number</li>
     *   <li>Null required field</li>
     * </ul>
     */
    record ValidationError(String message, List<String> details) implements UseCaseError {
        public ValidationError(String message) {
            this(message, List.of());
        }

        public ValidationError(String message, String singleDetail) {
            this(message, List.of(singleDetail));
        }

        @Override
        public ErrorType type() {
            return ErrorType.VALIDATION;
        }

        /**
         * Factory method for validation error with single detail.
         *
         * @param message the main error message
         * @param detail the detailed error message
         * @return a new ValidationError
         */
        public static ValidationError of(String message, String detail) {
            return new ValidationError(message, detail);
        }

        /**
         * Factory method for validation error without details.
         *
         * @param message the error message
         * @return a new ValidationError
         */
        public static ValidationError of(String message) {
            return new ValidationError(message);
        }
    }

    /**
     * Business rule error - operation violates business logic.
     *
     * <p>Maps to HTTP 400 Bad Request.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Multiple swaps attempted (violates single-swap rule)</li>
     *   <li>Not exactly 20 teams</li>
     *   <li>Duplicate positions</li>
     * </ul>
     */
    record BusinessRuleError(String message, String detail) implements UseCaseError {
        public BusinessRuleError(String message) {
            this(message, message);
        }

        @Override
        public List<String> details() {
            return List.of(detail);
        }

        @Override
        public ErrorType type() {
            return ErrorType.BUSINESS_RULE;
        }

        /**
         * Factory method for business rule error.
         *
         * @param message the main error message
         * @param detail additional detail about the rule violation
         * @return a new BusinessRuleError
         */
        public static BusinessRuleError of(String message, String detail) {
            return new BusinessRuleError(message, detail);
        }

        /**
         * Factory method for business rule error without separate detail.
         *
         * @param message the error message
         * @return a new BusinessRuleError
         */
        public static BusinessRuleError of(String message) {
            return new BusinessRuleError(message);
        }
    }

    /**
     * Not found error - requested resource does not exist.
     *
     * <p>Maps to HTTP 404 Not Found.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Season prediction not found for user</li>
     *   <li>Team not found</li>
     *   <li>Season not found</li>
     * </ul>
     */
    record NotFoundError(String message, String resourceType, String resourceId) implements UseCaseError {
        public NotFoundError(String message) {
            this(message, "Resource", "unknown");
        }

        @Override
        public List<String> details() {
            return List.of(resourceType + " with ID '" + resourceId + "' not found");
        }

        @Override
        public ErrorType type() {
            return ErrorType.NOT_FOUND;
        }

        /**
         * Factory method for not found error with resource info.
         *
         * @param resourceType the type of resource (e.g., "SeasonPrediction")
         * @param resourceId the ID that was not found
         * @return a new NotFoundError
         */
        public static NotFoundError of(String resourceType, String resourceId) {
            return new NotFoundError(resourceType + " not found", resourceType, resourceId);
        }

        /**
         * Factory method for not found error with custom message.
         *
         * @param message the error message
         * @return a new NotFoundError
         */
        public static NotFoundError of(String message) {
            return new NotFoundError(message);
        }
    }

    /**
     * Conflict error - resource already exists or state conflict.
     *
     * <p>Maps to HTTP 409 Conflict.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Season prediction already exists for user</li>
     *   <li>Contest entry already exists</li>
     *   <li>Duplicate resource creation attempt</li>
     * </ul>
     */
    record ConflictError(String message, String conflictReason) implements UseCaseError {
        public ConflictError(String message) {
            this(message, message);
        }

        @Override
        public List<String> details() {
            return List.of(conflictReason);
        }

        @Override
        public ErrorType type() {
            return ErrorType.CONFLICT;
        }

        /**
         * Factory method for conflict error.
         *
         * @param message the main error message
         * @param conflictReason detailed reason for the conflict
         * @return a new ConflictError
         */
        public static ConflictError of(String message, String conflictReason) {
            return new ConflictError(message, conflictReason);
        }

        /**
         * Factory method for conflict error without separate reason.
         *
         * @param message the error message
         * @return a new ConflictError
         */
        public static ConflictError of(String message) {
            return new ConflictError(message);
        }
    }
}
