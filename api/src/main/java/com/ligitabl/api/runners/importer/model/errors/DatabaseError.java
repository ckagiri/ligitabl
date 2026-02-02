package com.ligitabl.api.runners.importer.model.errors;

import lombok.Value;

@Value
public class DatabaseError implements ImportError {
    String message;
    String code;
    String entity;

    @Override
    public String message() {
        return message;
    }

    @Override
    public String code() {
        return code;
    }

    public static DatabaseError of(String message, String entity) {
        return new DatabaseError(message, "DATABASE_ERROR", entity);
    }

    public static DatabaseError notFound(String entity, Object id) {
        return new DatabaseError(String.format("%s not found with id: %s", entity, id), "ENTITY_NOT_FOUND", entity);
    }

    public static DatabaseError persistenceFailed(String entity, String reason) {
        return new DatabaseError(
                String.format("Failed to persist %s: %s", entity, reason), "PERSISTENCE_FAILED", entity);
    }
}
