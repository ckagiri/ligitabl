package com.ligitabl.model.shared;

/** A value from outside the system is malformed, such as a submitted email. The message is safe to show users. */
public class InvalidValueException extends IllegalArgumentException {
    public InvalidValueException(String message) {
        super(message);
    }
}
