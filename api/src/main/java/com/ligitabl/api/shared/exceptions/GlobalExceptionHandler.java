package com.ligitabl.api.shared.exceptions;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.ligitabl.api.shared.errors.*;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice(annotations = RestController.class)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<UseCaseErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(UseCaseErrorResponse.builder()
                        .message(message)
                        .error("VALIDATION_FAILED")
                        .status(HttpStatus.BAD_REQUEST)
                        .path(request.getDescription(false))
                        .build());
    }

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<UseCaseErrorResponse> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        var error = UseCaseErrors.unexpected(ex);
        return UseCaseErrorResponseFactory.from(error, request.getDescription(false));
    }
}
