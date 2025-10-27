package com.ligitabl.api.shared.errors;

import java.util.List;
import java.util.stream.Collectors;

public record ValidationError(List<ValidationMessage> messages) implements UseCaseError {

    public ValidationError {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("ValidationError must contain at least one message");
        }
        messages = List.copyOf(messages); // defensive copy
    }

    // Convenience constructor for a single message
    public ValidationError(ValidationMessage message) {
        this(List.of(message));
    }

    // Convenience constructor for a single string
    public ValidationError(String message) {
        this(List.of(ValidationMessage.of(message)));
    }

    @Override
    public String getMessage() {
        // You can decide how to represent multiple messages
        return messages.stream().map(m -> m.field() + ": " + m.message())
            .collect(Collectors.joining(", "));
    }
}
