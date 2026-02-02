package com.ligitabl.api.runners.importer.model.valueobjects;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.errors.ValidationError;
import com.ligitabl.api.shared.Either;

import lombok.Value;

@Value
public class CompetitionCode {
    String value;

    public static Either<ImportError, CompetitionCode> of(String code) {
        if (code == null || code.isBlank()) {
            return left(ValidationError.missingField("competitionCode"));
        }
        if (!code.matches("^[A-Z0-9]{2,4}$")) {
            return left(
                    ValidationError.invalidData("competitionCode", "Must be 2-4 uppercase alphanumeric characters"));
        }
        return right(new CompetitionCode(code));
    }
}
