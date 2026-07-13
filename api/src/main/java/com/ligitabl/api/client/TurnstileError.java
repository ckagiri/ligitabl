package com.ligitabl.api.client;

import java.util.List;

public sealed interface TurnstileError {
    record NetworkError(String message, Throwable cause) implements TurnstileError {}

    record Timeout(String message) implements TurnstileError {}

    record VerificationFailed(List<String> errorCodes) implements TurnstileError {}

    record UnexpectedError(String message) implements TurnstileError {}
}
