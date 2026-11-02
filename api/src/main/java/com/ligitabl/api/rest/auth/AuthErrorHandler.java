package com.ligitabl.api.rest.auth;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ligitabl.api.logging.RequestId;
import com.ligitabl.api.shared.errors.AuthenticationError;
import com.ligitabl.api.shared.errors.AuthorizationError;
import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UnexpectedError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.exceptions.UseCaseErrorStatus;
import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice(basePackages = "com.ligitabl.api.rest.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class AuthErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthDto.ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String field = fieldError == null ? null : fieldError.getField();
        String message = fieldError == null ? "Validation failed" : fieldError.getDefaultMessage();

        return ResponseEntity.badRequest().body(new AuthDto.ErrorResponse("VALIDATION_ERROR", message, field));
    }

    @ExceptionHandler(UseCaseException.class)
    public ResponseEntity<AuthDto.ErrorResponse> handleUseCase(UseCaseException ex) {
        UseCaseError error = ex.getError();
        return ResponseEntity.status(UseCaseErrorStatus.of(error)).body(toBody(error));
    }

    private static AuthDto.ErrorResponse toBody(UseCaseError error) {
        return switch (error) {
            case ValidationError ve -> {
                var first = ve.messages().getFirst();
                yield new AuthDto.ErrorResponse("VALIDATION_ERROR", first.message(), first.field());
            }
            case AuthenticationError ae -> new AuthDto.ErrorResponse("AUTHENTICATION_ERROR", ae.getMessage());
            case AuthorizationError ae -> new AuthDto.ErrorResponse("ACCESS_DENIED", ae.getMessage());
            case NotFoundError nf -> new AuthDto.ErrorResponse("NOT_FOUND", nf.getMessage());
            case ConflictError ce -> new AuthDto.ErrorResponse("ALREADY_EXISTS", ce.getMessage());
            case UnexpectedError ue -> {
                log.error("Unexpected error: {}", ue.logMessage(), ue.cause());
                yield new AuthDto.ErrorResponse(
                        "INTERNAL_ERROR", UnexpectedError.PUBLIC_MESSAGE, null, RequestId.current());
            }
        };
    }
}
