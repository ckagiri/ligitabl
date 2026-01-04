package com.ligitabl.api.usecases.auth;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ligitabl.api.shared.errors.AuthorizationError;
import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.exceptions.UseCaseException;

@RestControllerAdvice(basePackages = "com.ligitabl.api.usecases.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
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

        if (error instanceof ValidationError ve) {
            var first = ve.messages().getFirst();
            return ResponseEntity.badRequest().body(new AuthDto.ErrorResponse(
                    "VALIDATION_ERROR", first.message(), first.field()));
        }

        if (error instanceof AuthorizationError ae) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthDto.ErrorResponse("AUTHENTICATION_ERROR", ae.getMessage()));
        }

        if (error instanceof ConflictError ce) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthDto.ErrorResponse("ALREADY_EXISTS", ce.getMessage()));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AuthDto.ErrorResponse("INTERNAL_ERROR", error.getMessage()));
    }
}
