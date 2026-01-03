package com.ligitabl.api.importer.model.errors;

/**
 * Base sealed interface for all import domain errors.
 * Using sealed types ensures exhaustive error handling.
 *
 * No external dependencies - pure domain model.
 */
public sealed interface ImportError permits ApiError, ValidationError, DatabaseError, MappingError {

    String message();

    String code();

    /**
     * Convert error to user-friendly message
     */
    default String toDisplayMessage() {
        return switch (this) {
            case ApiError e -> "Failed to fetch data from external API: " + e.message();
            case ValidationError e -> "Data validation failed: " + e.message();
            case DatabaseError e -> "Database operation failed: " + e.message();
            case MappingError e -> "Data mapping failed: " + e.message();
        };
    }
}
