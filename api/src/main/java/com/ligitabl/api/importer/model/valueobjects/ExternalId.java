package com.ligitabl.api.importer.model.valueobjects;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.errors.ValidationError;
import com.ligitabl.api.shared.Either;
import lombok.Value;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

@Value
public class ExternalId {
    Integer value;

    public static Either<ImportError, ExternalId> of(Integer id) {
        if (id == null) {
            return left(ValidationError.missingField("externalId"));
        }
        if (id <= 0) {
            return left(ValidationError.invalidData(
                    "externalId",
                    "Must be positive"
            ));
        }
        return right(new ExternalId(id));
    }
}
