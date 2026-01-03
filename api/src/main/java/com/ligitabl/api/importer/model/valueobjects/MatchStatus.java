package com.ligitabl.api.importer.model.valueobjects;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.errors.MappingError;
import com.ligitabl.api.shared.Either;

import lombok.Value;

@Value
public class MatchStatus {
    Status status;

    public enum Status {
        SCHEDULED,
        TIMED,
        IN_PLAY,
        PAUSED,
        FINISHED,
        POSTPONED,
        SUSPENDED,
        CANCELLED,
        AWARDED
    }

    public static Either<ImportError, MatchStatus> of(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return right(new MatchStatus(Status.SCHEDULED));
        }

        try {
            Status status = Status.valueOf(statusStr.toUpperCase());
            return right(new MatchStatus(status));
        } catch (IllegalArgumentException e) {
            return left(MappingError.unmappableStatus(statusStr));
        }
    }

    public static MatchStatus scheduled() {
        return new MatchStatus(Status.SCHEDULED);
    }
}
