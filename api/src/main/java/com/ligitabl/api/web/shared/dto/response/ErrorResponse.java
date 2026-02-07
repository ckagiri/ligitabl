package com.ligitabl.api.web.shared.dto.response;

import java.util.List;
import java.util.Objects;

/**
 * DTO for error responses.
 *
 * <p>Maps from UseCaseError to HTTP response format.
 * Includes error type, message, and optional details.</p>
 */
public class ErrorResponse {

    private final String type;
    private final String message;
    private final List<String> details;

    public ErrorResponse(String type, String message, List<String> details) {
        this.type = Objects.requireNonNull(type, "type is required");
        this.message = Objects.requireNonNull(message, "message is required");
        this.details = Objects.requireNonNull(details, "details are required");
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getDetails() {
        return details;
    }

    /**
     * Check if this is a validation error.
     *
     * @return true if type is VALIDATION
     */
    public boolean isValidationError() {
        return "VALIDATION".equals(type);
    }

    /**
     * Check if this is a business rule error.
     *
     * @return true if type is BUSINESS_RULE
     */
    public boolean isBusinessRuleError() {
        return "BUSINESS_RULE".equals(type);
    }

    /**
     * Check if this is a not found error.
     *
     * @return true if type is NOT_FOUND
     */
    public boolean isNotFoundError() {
        return "NOT_FOUND".equals(type);
    }

    /**
     * Check if this is a conflict error.
     *
     * @return true if type is CONFLICT
     */
    public boolean isConflictError() {
        return "CONFLICT".equals(type);
    }

    @Override
    public String toString() {
        return "ErrorResponse{" + "type='"
                + type + '\'' + ", message='"
                + message + '\'' + ", details="
                + details + '}';
    }
}
