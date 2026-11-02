package com.ligitabl.model.shared;

/** The request is fine, but the current state doesn't allow it. The message is safe to show users. */
public class RuleViolationException extends IllegalStateException {
    public RuleViolationException(String message) {
        super(message);
    }
}
