package com.ligitabl.api.shared.exceptions;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.ligitabl.api.logging.RequestId;
import com.ligitabl.api.shared.errors.*;

public class UseCaseErrorResponseFactory {
    public static ResponseEntity<UseCaseErrorResponse> from(UseCaseError error, String path) {
        return build(title(error), error.getMessage(), UseCaseErrorStatus.of(error), path);
    }

    private static String title(UseCaseError error) {
        return switch (error) {
            case NotFoundError __ -> "Not Found";
            case ValidationError __ -> "Validation Failed";
            case UnprocessableEntityError __ -> "Unprocessable Entity";
            case ConflictError __ -> "Business Rule Violation";
            case AuthenticationError __ -> "Unauthorized";
            case AuthorizationError __ -> "Forbidden";
            case UnexpectedError __ -> "Unexpected Error";
        };
    }

    /** A null message falls back to the status's reason phrase. */
    public static ResponseEntity<UseCaseErrorResponse> clientError(HttpStatusCode status, String message, String path) {
        HttpStatus resolved = HttpStatus.valueOf(status.value());
        return build(
                resolved.getReasonPhrase(), message != null ? message : resolved.getReasonPhrase(), resolved, path);
    }

    private static ResponseEntity<UseCaseErrorResponse> build(
            String title, String message, HttpStatus status, String path) {
        var response = UseCaseErrorResponse.builder()
                .message(message)
                .error(title)
                .status(status)
                .path(path)
                .requestId(RequestId.current())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpStatusCode statusCode = Objects.requireNonNull(status, "status");
        return new ResponseEntity<>(response, headers, statusCode);
    }
}
