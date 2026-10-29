package com.ligitabl.api.shared.exceptions;

import com.ligitabl.api.shared.errors.UnexpectedError;
import com.ligitabl.api.shared.errors.UseCaseError;

import lombok.Getter;

@Getter
public class UseCaseException extends RuntimeException {
    private final UseCaseError error;

    public UseCaseException(UseCaseError error) {
        super(error.logMessage(), error instanceof UnexpectedError ue ? ue.cause() : null);
        this.error = error;
    }
}
