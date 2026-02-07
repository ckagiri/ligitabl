package com.ligitabl.api.client;

public sealed interface FootballDataApiError {
    record NetworkError(String message, Throwable cause) implements FootballDataApiError {}

    record RateLimitExceeded(String message) implements FootballDataApiError {}

    record NotFound(String message) implements FootballDataApiError {}

    record Unauthorized(String message) implements FootballDataApiError {}

    record ServerError(String message, int statusCode) implements FootballDataApiError {}

    record UnknownError(String message, Throwable cause) implements FootballDataApiError {}

    record UnexpectedError(String message) implements FootballDataApiError {}
}
