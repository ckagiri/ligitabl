package com.ligitabl.api.shared.exceptions;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UseCaseErrorResponse {
    private final String message;
    private final String error;
    private final HttpStatusCode status;
    private final String path;
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    public int getCode() {
        return status.value();
    }

    public ResponseEntity<UseCaseErrorResponse> toResponseEntity() {
        return new ResponseEntity<>(this, status);
    }
}
