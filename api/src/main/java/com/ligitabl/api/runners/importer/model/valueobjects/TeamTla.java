package com.ligitabl.api.runners.importer.model.valueobjects;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.errors.ValidationError;
import com.ligitabl.api.shared.Either;

import lombok.Value;

@Value
public class TeamTla {
    String value;

    public static Either<ImportError, TeamTla> of(String tla) {
        if (tla == null || tla.isBlank()) {
            return left(ValidationError.missingField("teamTla"));
        }
        if (tla.length() != 3) {
            return left(ValidationError.invalidData("teamTla", "Must be exactly 3 characters"));
        }
        return right(new TeamTla(tla.toUpperCase()));
    }
}
