package com.ligitabl.api.runners.importer.model.valueobjects;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.errors.ValidationError;
import com.ligitabl.api.shared.Either;

import lombok.Value;

@Value
public class Matchday {
    int value;

    public static Either<ImportError, Matchday> of(Integer matchday) {
        if (matchday == null) {
            return left(ValidationError.missingField("matchday"));
        }
        if (matchday < 1 || matchday > 50) {
            return left(ValidationError.invalidData("matchday", "Must be between 1 and 50"));
        }
        return right(new Matchday(matchday));
    }
}
