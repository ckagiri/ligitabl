package com.ligitabl.api.importer.model.valueobjects;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

import java.time.OffsetDateTime;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.errors.ValidationError;
import com.ligitabl.api.shared.Either;

import lombok.Value;

@Value
public class KickOffTime {
    OffsetDateTime value;

    public static Either<ImportError, KickOffTime> of(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return left(ValidationError.missingField("kickOffTime"));
        }
        return right(new KickOffTime(dateTime));
    }

    public static KickOffTime now() {
        return new KickOffTime(OffsetDateTime.now());
    }
}
