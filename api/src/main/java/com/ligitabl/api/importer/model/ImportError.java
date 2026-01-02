package com.ligitabl.api.importer.model;

import lombok.Value;

/**
 * Base sealed interface for all import domain errors.
 * Using sealed types ensures exhaustive error handling.
 *
 * No external dependencies - pure domain model.
 */
public sealed interface ImportError permits
        ImportError.ApiError,
        ImportError.ValidationError,
        ImportError.DatabaseError,
        ImportError.MappingError {

    String message();
    String code();

    @Value
    class ApiError implements ImportError {
        String message;
        String code;
        int statusCode;

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

    @Value
    class ValidationError implements ImportError {
        String message;
        String code;
        String field;

        public static ValidationError of(String message, String field) {
            return new ValidationError(message, "VALIDATION_ERROR", field);
        }

        public static ValidationError missingField(String field) {
            return new ValidationError(
                    "Required field is missing: " + field,
                    "MISSING_FIELD",
                    field
            );
        }

        public static ValidationError invalidData(String field, String reason) {
            return new ValidationError(
                    String.format("Invalid data in field '%s': %s", field, reason),
                    "INVALID_DATA",
                    field
            );
        }
    }

    @Value
    class DatabaseError implements ImportError {
        String message;
        String code;
        String entity;

        public static DatabaseError of(String message, String entity) {
            return new DatabaseError(message, "DATABASE_ERROR", entity);
        }

        public static DatabaseError notFound(String entity, Object id) {
            return new DatabaseError(
                    String.format("%s not found with id: %s", entity, id),
                    "ENTITY_NOT_FOUND",
                    entity
            );
        }

        public static DatabaseError persistenceFailed(String entity, String reason) {
            return new DatabaseError(
                    String.format("Failed to persist %s: %s", entity, reason),
                    "PERSISTENCE_FAILED",
                    entity
            );
        }
    }

    @Value
    class MappingError implements ImportError {
        String message;
        String code;
        String sourceField;

        public static MappingError of(String message, String sourceField) {
            return new MappingError(message, "MAPPING_ERROR", sourceField);
        }

        public static MappingError unmappableStatus(String status) {
            return new MappingError(
                    "Cannot map external status: " + status,
                    "UNMAPPABLE_STATUS",
                    "status"
            );
        }

        public static MappingError missingReference(String refType, Object refId) {
            return new MappingError(
                    String.format("Missing reference: %s with id %s", refType, refId),
                    "MISSING_REFERENCE",
                    refType
            );
        }
    }

    /**
     * Convert error to user-friendly message
     */
    default String toDisplayMessage() {
        return switch (this) {
            case ApiError e ->
                    "Failed to fetch data from external API: " + e.message();
            case ValidationError e ->
                    "Data validation failed: " + e.message();
            case DatabaseError e ->
                    "Database operation failed: " + e.message();
            case MappingError e ->
                    "Data mapping failed: " + e.message();
        };
    }
}
