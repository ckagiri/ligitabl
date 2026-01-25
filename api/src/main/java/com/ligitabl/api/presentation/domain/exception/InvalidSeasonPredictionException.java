package com.ligitabl.api.presentation.domain.exception;

/**
 * Thrown when a season prediction fails validation rules.
 *
 * Examples:
 * - Not exactly 20 teams
 * - Invalid positions (not 1-20)
 * - Duplicate positions
 * - Teams don't exist in season
 */
public class InvalidSeasonPredictionException extends DomainException {

    public InvalidSeasonPredictionException(String message) {
        super(message);
    }

    public InvalidSeasonPredictionException(String message, Throwable cause) {
        super(message, cause);
    }
}
