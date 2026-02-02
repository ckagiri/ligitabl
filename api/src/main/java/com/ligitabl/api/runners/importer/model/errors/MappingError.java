package com.ligitabl.api.runners.importer.model.errors;

import lombok.Value;

@Value
public class MappingError implements ImportError {
    String message;
    String code;
    String sourceField;

    @Override
    public String message() {
        return message;
    }

    @Override
    public String code() {
        return code;
    }

    public static MappingError of(String message, String sourceField) {
        return new MappingError(message, "MAPPING_ERROR", sourceField);
    }

    public static MappingError unmappableStatus(String status) {
        return new MappingError("Cannot map external status: " + status, "UNMAPPABLE_STATUS", "status");
    }

    public static MappingError missingReference(String refType, Object refId) {
        return new MappingError(
                String.format("Missing reference: %s with id %s", refType, refId), "MISSING_REFERENCE", refType);
    }
}
