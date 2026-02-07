package com.ligitabl.api.web.shared.domain.exception;

/**
 * Thrown when a season prediction cannot be found.
 * Maps to HTTP 404 Not Found in the web layer.
 */
public class SeasonPredictionNotFoundException extends DomainException {

    public SeasonPredictionNotFoundException(String message) {
        super(message);
    }
}
