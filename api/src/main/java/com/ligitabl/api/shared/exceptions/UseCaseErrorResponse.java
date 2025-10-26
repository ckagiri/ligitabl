package com.ligitabl.api.shared.exceptions;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

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
