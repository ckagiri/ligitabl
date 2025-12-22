package com.ligitabl.api.shared.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.ligitabl.api.shared.errors.*;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UseCaseException.class)
    public ResponseEntity<UseCaseErrorResponse> handleBusinessFailure(UseCaseException ex, WebRequest request) {
        var error = ex.getError();
        // Log at WARN for client/expected errors; ERROR for unexpected server errors
        if (error instanceof UnexpectedError ue) {
            log.error("Unexpected error: {}", ue.getMessage(), ue.cause());
        } else if (error instanceof NotFoundError nf) {
            log.warn("Not found: {}", nf.getMessage());
        } else if (error instanceof ValidationError ve) {
            log.warn("Validation failed: {}", ve.getMessage());
        } else if (error instanceof UnprocessableEntityError ue) {
            log.warn("Unprocessable entity: {}", ue.getMessage());
        } else if (error instanceof ConflictError ce) {
            log.warn("Conflict: {}", ce.getMessage());
        } else if (error instanceof AuthorizationError ue) {
            log.warn("Unauthorized: {}", ue.getMessage());
        } else {
            log.error("Unhandled error type: {}", error.getClass().getSimpleName());
        }

        return UseCaseErrorResponseFactory.from(error, request.getDescription(false));
    }
}
