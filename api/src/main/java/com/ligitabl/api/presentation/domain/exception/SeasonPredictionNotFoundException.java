package com.ligitabl.api.presentation.domain.exception;

/**
 * Thrown when a season prediction cannot be found.
 *
 * Examples:
 * - User tries to swap teams but has no season prediction
 * - Invalid prediction ID
 *
 * Maps to HTTP 404 Not Found in the presentation layer.
 */
public class SeasonPredictionNotFoundException extends DomainException {

    public SeasonPredictionNotFoundException(String message) {
        super(message);
    }

    public SeasonPredictionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
