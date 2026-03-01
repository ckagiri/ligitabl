package com.ligitabl.api.web.shared.dto;

import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * DTO for error responses.
 *
 * <p>Maps from UseCaseError to HTTP response format.
 * Includes error type, message, and optional details.</p>
 */
@Getter
public class ErrorResponse {

    private final String type;
    private final String message;
    private final List<String> details;

    public ErrorResponse(String type, String message, List<String> details) {
        this.type = Objects.requireNonNull(type, "type is required");
        this.message = Objects.requireNonNull(message, "message is required");
        this.details = Objects.requireNonNull(details, "details are required");
    }

    @Override
    public String toString() {
        return "ErrorResponse{" + "type='"
                + type + '\'' + ", message='"
                + message + '\'' + ", details="
                + details + '}';
    }
}
