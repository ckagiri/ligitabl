package com.ligitabl.api.shared.exceptions;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.ligitabl.api.shared.errors.*;

public class UseCaseErrorResponseFactory {
    public static ResponseEntity<UseCaseErrorResponse> from(UseCaseError error, String path) {
        return switch (error) {
            case NotFoundError nf -> build("Not Found", nf.getMessage(), HttpStatus.NOT_FOUND, path);
            case ValidationError ve -> build("Validation Failed", ve.getMessage(), HttpStatus.BAD_REQUEST, path);
            case UnprocessableEntityError ue -> build(
                    "Unprocessable Entity", ue.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY, path);
            case ConflictError ce -> build("Business Rule Violation", ce.getMessage(), HttpStatus.CONFLICT, path);
            case AuthorizationError ue -> build("Unauthorized", ue.getMessage(), HttpStatus.UNAUTHORIZED, path);
            case AuthenticationError ae -> build("Forbidden", ae.getMessage(), HttpStatus.FORBIDDEN, path);
            case UnexpectedError ue -> build(
                    "Unexpected Error", ue.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, path);
        };
    }

    private static ResponseEntity<UseCaseErrorResponse> build(
            String title, String message, HttpStatus status, String path) {
        var response = UseCaseErrorResponse.builder()
                .message(message)
                .error(title)
                .status(status)
                .path(path)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpStatusCode statusCode = Objects.requireNonNull(status, "status");
        return new ResponseEntity<>(response, headers, statusCode);
    }
}
