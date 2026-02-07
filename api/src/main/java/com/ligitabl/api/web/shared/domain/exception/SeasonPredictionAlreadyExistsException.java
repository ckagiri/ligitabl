package com.ligitabl.api.web.shared.domain.exception;

/**
 * Thrown when attempting to create a season prediction for a user who already has one.
 * Business Rule: One season prediction per user per season.
 * Maps to HTTP 409 Conflict in the web layer.
 */
public class SeasonPredictionAlreadyExistsException extends DomainException {

    public SeasonPredictionAlreadyExistsException(String message) {
        super(message);
    }
}
