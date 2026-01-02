package com.ligitabl.api.importer.model;

import com.ligitabl.api.shared.Either;
import lombok.Value;

import java.util.Objects;
import java.time.OffsetDateTime;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

/**
 * Domain value objects for match import.
 * Uses your Either type from com.ligitabl.api.shared.Either
 */
public final class ValueObjects {

    private ValueObjects() {}

    @Value
    public static class CompetitionCode {
        String value;

        public static Either<ImportError, CompetitionCode> of(String code) {
            if (code == null || code.isBlank()) {
                return left(ImportError.ValidationError.missingField("competitionCode"));
            }
            if (!code.matches("^[A-Z0-9]{2,4}$")) {
                return left(ImportError.ValidationError.invalidData(
                        "competitionCode",
                        "Must be 2-4 uppercase alphanumeric characters"
                ));
            }
            return right(new CompetitionCode(code));
        }
    }

    @Value
    public static class ExternalId {
        Integer value;

        public static Either<ImportError, ExternalId> of(Integer id) {
            if (id == null) {
                return left(ImportError.ValidationError.missingField("externalId"));
            }
            if (id <= 0) {
                return left(ImportError.ValidationError.invalidData(
                        "externalId",
                        "Must be positive"
                ));
            }
            return right(new ExternalId(id));
        }
    }

    @Value
    public static class MatchSlug {
        String value;

        public static MatchSlug of(String homeTla, String awayTla) {
            var slug = String.format("%s-%s", homeTla.toLowerCase(), awayTla.toLowerCase());
            return new MatchSlug(slug);
        }
    }

    @Value
    public static class TeamTla {
        String value;

        public static Either<ImportError, TeamTla> of(String tla) {
            if (tla == null || tla.isBlank()) {
                return left(ImportError.ValidationError.missingField("teamTla"));
            }
            if (tla.length() != 3) {
                return left(ImportError.ValidationError.invalidData(
                        "teamTla",
                        "Must be exactly 3 characters"
                ));
            }
            return right(new TeamTla(tla.toUpperCase()));
        }
    }

    @Value
    public static class MatchStatus {
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
                return left(ImportError.MappingError.unmappableStatus(statusStr));
            }
        }

        public static MatchStatus scheduled() {
            return new MatchStatus(Status.SCHEDULED);
        }
    }

    @Value
    public static class KickOffTime {
        OffsetDateTime value;

        public static Either<ImportError, KickOffTime> of(OffsetDateTime dateTime) {
            if (dateTime == null) {
                return left(ImportError.ValidationError.missingField("kickOffTime"));
            }
            return right(new KickOffTime(dateTime));
        }

        public static KickOffTime now() {
            return new KickOffTime(OffsetDateTime.now());
        }
    }

    @Value
    public static class Matchday {
        int value;

        public static Either<ImportError, Matchday> of(Integer matchday) {
            if (matchday == null) {
                return left(ImportError.ValidationError.missingField("matchday"));
            }
            if (matchday < 1 || matchday > 50) {
                return left(ImportError.ValidationError.invalidData(
                        "matchday",
                        "Must be between 1 and 50"
                ));
            }
            return right(new Matchday(matchday));
        }
    }
}
