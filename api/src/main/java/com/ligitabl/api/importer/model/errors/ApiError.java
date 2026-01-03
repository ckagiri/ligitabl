package com.ligitabl.api.importer.model.errors;

import lombok.Value;

@Value
public class ApiError implements ImportError {
    String message;
    String code;
    int statusCode;

    @Override
    public String message() {
        return message;
    }

    @Override
    public String code() {
        return code;
    }

    public static ApiError of(String message, int statusCode) {
        return new ApiError(message, "API_ERROR", statusCode);
    }

    public static ApiError connectionFailed(String message) {
        return new ApiError(message, "API_CONNECTION_FAILED", 0);
    }

    public static ApiError timeout(String message) {
        return new ApiError(message, "API_TIMEOUT", 0);
    }

    public static ApiError rateLimited() {
        return new ApiError("API rate limit exceeded", "API_RATE_LIMITED", 429);
    }
}
