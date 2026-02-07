package com.ligitabl.api.web.shared.domain.exception;

/**
 * Thrown when a season prediction fails validation rules.
 */
public class InvalidSeasonPredictionException extends DomainException {
    public InvalidSeasonPredictionException(String message, Throwable cause) {
        super(message, cause);
    }
}
