package com.ligitabl.api.shared.exceptions;

import com.ligitabl.api.shared.errors.UseCaseError;

import lombok.Getter;

@Getter
public class BusinessFailureException extends RuntimeException {
    private final UseCaseError error;

    public BusinessFailureException(UseCaseError error) {
        super(error.getMessage());
        this.error = error;
    }

}
