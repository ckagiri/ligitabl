package com.ligitabl.api.shared.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.ligitabl.api.shared.errors.*;

public class UseCaseErrorResponseFactory {
    public static ResponseEntity<UseCaseErrorResponse> from(UseCaseError error, String path) {
        return switch (error) {
            case NotFoundError nf -> build("Not Found", nf.getMessage(), HttpStatus.NOT_FOUND, path);
            case ValidationError ve -> build("Validation Failed", ve.getMessage(), HttpStatus.BAD_REQUEST, path);
            case UnexpectedError ue -> build(
                    "Unexpected Error", ue.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, path);
            case ConflictError ce -> build("Business Rule Violation", ce.getMessage(), HttpStatus.CONFLICT, path);
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

        return new ResponseEntity<>(response, status);
    }
}
