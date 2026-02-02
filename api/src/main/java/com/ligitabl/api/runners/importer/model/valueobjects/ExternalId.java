package com.ligitabl.api.runners.importer.model.valueobjects;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.errors.ValidationError;
import com.ligitabl.api.shared.Either;

import lombok.Value;

@Value
public class ExternalId {
    Integer value;

    public static Either<ImportError, ExternalId> of(Integer id) {
        if (id == null) {
            return left(ValidationError.missingField("externalId"));
        }
        if (id <= 0) {
            return left(ValidationError.invalidData("externalId", "Must be positive"));
        }
        return right(new ExternalId(id));
    }
}
