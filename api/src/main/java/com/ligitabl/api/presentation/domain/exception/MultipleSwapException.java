package com.ligitabl.api.presentation.domain.exception;

/**
 * Thrown when a swap request contains more than one pair of teams.
 *
 * This is a CRITICAL business rule: existing participants can only swap ONE pair per request.
 *
 * Example:
 * If a request changes positions of 4 teams (2 swaps), this exception is thrown.
 */
public class MultipleSwapException extends DomainException {

    public MultipleSwapException(String message) {
        super(message);
    }

    public MultipleSwapException(String message, Throwable cause) {
        super(message, cause);
    }
}
