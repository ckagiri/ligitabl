package com.ligitabl.api.web.shared.error;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.errors.AuthenticationError;
import com.ligitabl.api.shared.errors.AuthorizationError;
import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UnexpectedError;
import com.ligitabl.api.shared.errors.UnprocessableEntityError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.errors.ValidationMessage;
import com.ligitabl.api.web.shared.dto.ErrorResponse;

/**
 * Maps UseCaseError to ErrorResponse DTO.
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
        return new ErrorResponse(resolveType(error), resolveMessage(error), resolveDetails(error));
    }

    private String resolveType(UseCaseError error) {
        if (error instanceof ValidationError) {
            return "VALIDATION";
        }
        if (error instanceof NotFoundError) {
            return "NOT_FOUND";
        }
        if (error instanceof ConflictError) {
            return "CONFLICT";
        }
        if (error instanceof UnprocessableEntityError) {
            return "BUSINESS_RULE";
        }
        if (error instanceof AuthenticationError) {
            return "AUTHENTICATION";
        }
        if (error instanceof AuthorizationError) {
            return "AUTHORIZATION";
        }
        if (error instanceof UnexpectedError) {
            return "UNEXPECTED";
        }
        return "UNKNOWN";
    }

    private String resolveMessage(UseCaseError error) {
        return error == null ? "Unknown error" : error.getMessage();
    }

    private List<String> resolveDetails(UseCaseError error) {
        if (error instanceof ValidationError validationError) {
            return validationError.messages().stream()
                    .map(this::formatValidationMessage)
                    .toList();
        }

        if (error instanceof NotFoundError notFoundError) {
            return notFoundError.entity() == null ? List.of() : List.of(notFoundError.message());
        }

        if (error instanceof ConflictError conflictError) {
            return conflictError.message() == null ? List.of() : List.of(conflictError.message());
        }

        if (error instanceof UnprocessableEntityError unprocessableEntityError) {
            return unprocessableEntityError.message() == null ? List.of() : List.of(unprocessableEntityError.message());
        }

        if (error instanceof AuthenticationError authenticationError) {
            return authenticationError.message() == null ? List.of() : List.of(authenticationError.message());
        }

        if (error instanceof AuthorizationError authorizationError) {
            return authorizationError.message() == null ? List.of() : List.of(authorizationError.message());
        }

        return List.of();
    }

    private String formatValidationMessage(ValidationMessage message) {
        if (message == null) {
            return "";
        }
        return message.field() + ": " + message.message();
    }
}
