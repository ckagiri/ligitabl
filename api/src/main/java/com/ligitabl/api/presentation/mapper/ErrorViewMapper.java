package com.ligitabl.api.presentation.mapper;

import com.ligitabl.api.presentation.dto.response.ErrorResponse;
import com.ligitabl.api.presentation.error.UseCaseError;
import org.springframework.stereotype.Component;

/**
 * Maps UseCaseError to ErrorResponse DTO.
 *
 * <p>This mapper is part of the presentation layer and converts
 * application-layer errors into HTTP response format.</p>
 */
@Component
public class ErrorViewMapper {

    /**
     * Map a UseCaseError to an ErrorResponse DTO.
     *
     * @param error the use case error
     * @return error response DTO
     */
    public ErrorResponse toResponse(UseCaseError error) {
        return new ErrorResponse(
                error.type().name(),
                error.message(),
                error.details()
        );
    }

    /**
     * Map error type to HTTP status code.
     *
     * @param error the use case error
     * @return HTTP status code (400, 404, 409, 500)
     */
    public int toHttpStatus(UseCaseError error) {
        return switch (error.type()) {
            case VALIDATION -> 400;      // Bad Request
            case NOT_FOUND -> 404;       // Not Found
            case CONFLICT -> 409;        // Conflict
            case BUSINESS_RULE -> 422;   // Unprocessable Entity
        };
    }
}
