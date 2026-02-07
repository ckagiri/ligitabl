package com.ligitabl.api.web.shared.domain.exception;

/**
 * Thrown when a swap request contains more than one pair of teams.
 * This is a CRITICAL business rule: existing participants can only swap ONE pair per request.
 */
public class MultipleSwapException extends DomainException {

    public MultipleSwapException(String message, Throwable cause) {
        super(message, cause);
    }
}
