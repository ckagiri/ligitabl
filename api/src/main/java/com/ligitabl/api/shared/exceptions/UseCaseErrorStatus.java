package com.ligitabl.api.shared.exceptions;

import org.springframework.http.HttpStatus;

import com.ligitabl.api.shared.errors.*;

public final class UseCaseErrorStatus {
    private UseCaseErrorStatus() {}

    public static HttpStatus of(UseCaseError error) {
        return switch (error) {
            case ValidationError __ -> HttpStatus.BAD_REQUEST;
            case AuthenticationError __ -> HttpStatus.UNAUTHORIZED;
            case AuthorizationError __ -> HttpStatus.FORBIDDEN;
            case NotFoundError __ -> HttpStatus.NOT_FOUND;
            case ConflictError __ -> HttpStatus.CONFLICT;
            case UnprocessableEntityError __ -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UnexpectedError __ -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
