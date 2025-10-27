package com.ligitabl.api.shared.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessFailureException.class)
    public ResponseEntity<UseCaseErrorResponse> handleBusinessFailure(BusinessFailureException ex,
        WebRequest request) {
        return UseCaseErrorResponseFactory.from(ex.getError(), request.getDescription(false));
    }
}
