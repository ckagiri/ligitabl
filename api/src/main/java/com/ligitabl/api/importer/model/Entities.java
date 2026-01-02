package com.ligitabl.api.importer.model;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.MatchStatus;
import lombok.Builder;
import lombok.Value;

import static com.ligitabl.api.shared.Either.left;

/**
 * Domain entities for match import.
 * Uses your Either type from com.ligitabl.api.shared.Either
 */
public final class Entities {

    private Entities() {}

    /**
     * Represents external competition data from the API
     */
    @Value
    @Builder
    public static class ExternalCompetition {
        ValueObjects.ExternalId id;
        String name;
        ValueObjects.CompetitionCode code;
        ExternalSeason currentSeason;

        public static Either<ImportError, ExternalCompetition> create(
                Integer id,
                String name,
                String code,
                ExternalSeason currentSeason) {

            return ValueObjects.ExternalId.of(id)
                    .flatMap(extId -> ValueObjects.CompetitionCode.of(code)
                            .map(compCode -> ExternalCompetition.builder()
                                    .id(extId)
                                    .name(name)
                                    .code(compCode)
                                    .currentSeason(currentSeason)
                                    .build()));
        }
    }

    /**
     * Represents external season data
     */
    @Value
    @Builder
    public static class ExternalSeason {
        ValueObjects.ExternalId id;
        String startDate;
        String endDate;

        public static Either<ImportError, ExternalSeason> create(
                Integer id,
                String startDate,
                String endDate) {

            return ValueObjects.ExternalId.of(id)
                    .map(extId -> ExternalSeason.builder()
                            .id(extId)
                            .startDate(startDate)
                            .endDate(endDate)
                            .build());
        }
    }

    /**
     * Represents external match data from the API
     */
    @Value
    @Builder
    public static class ExternalMatch {
        ValueObjects.ExternalId id;
        ValueObjects.KickOffTime kickOff;
        MatchStatus status;
        ValueObjects.Matchday matchday;
        ExternalTeam homeTeam;
        ExternalTeam awayTeam;

        public static Either<ImportError, ExternalMatch> create(
                Integer id,
                java.time.OffsetDateTime utcDate,
                String status,
                Integer matchday,
                ExternalTeam homeTeam,
                ExternalTeam awayTeam) {

            if (homeTeam == null || awayTeam == null) {
                return left(ImportError.ValidationError.of(
                        "Both home and away teams are required",
                        "teams"
                ));
            }

            return ValueObjects.ExternalId.of(id)
                    .flatMap(extId -> ValueObjects.KickOffTime.of(utcDate)
                            .flatMap(kickOff -> MatchStatus.of(status)
                                    .flatMap(matchStatus -> ValueObjects.Matchday.of(matchday)
                                            .map(md -> ExternalMatch.builder()
                                                    .id(extId)
                                                    .kickOff(kickOff)
                                                    .status(matchStatus)
                                                    .matchday(md)
                                                    .homeTeam(homeTeam)
                                                    .awayTeam(awayTeam)
                                                    .build()))));
        }
    }

    /**
     * Represents external team data
     */
    @Value
    @Builder
    public static class ExternalTeam {
        ValueObjects.ExternalId id;
        String name;
        ValueObjects.TeamTla tla;

        public static Either<ImportError, ExternalTeam> create(
                Integer id,
                String name,
                String tla) {

            return ValueObjects.ExternalId.of(id)
                    .flatMap(extId -> ValueObjects.TeamTla.of(tla)
                            .map(teamTla -> ExternalTeam.builder()
                                    .id(extId)
                                    .name(name)
                                    .tla(teamTla)
                                    .build()));
        }
    }

    /**
     * Result of a match import operation
     */
    @Value
    @Builder
    public static class MatchImportResult {
        ValueObjects.ExternalId matchId;
        boolean created;
        boolean updated;
        ValueObjects.MatchSlug slug;

        public static MatchImportResult created(ValueObjects.ExternalId matchId, ValueObjects.MatchSlug slug) {
            return MatchImportResult.builder()
                    .matchId(matchId)
                    .created(true)
                    .updated(false)
                    .slug(slug)
                    .build();
        }

        public static MatchImportResult updated(ValueObjects.ExternalId matchId, ValueObjects.MatchSlug slug) {
            return MatchImportResult.builder()
                    .matchId(matchId)
                    .created(false)
                    .updated(true)
                    .slug(slug)
                    .build();
        }
    }

    /**
     * Aggregate result of importing multiple matches
     */
    @Value
    @Builder
    public static class ImportSummary {
        ValueObjects.CompetitionCode competition;
        String seasonName;
        int totalMatches;
        int created;
        int updated;
        int failed;
        java.util.List<ImportError> errors;

        public boolean isSuccessful() {
            return failed == 0 && errors.isEmpty();
        }

        public boolean isPartialSuccess() {
            return (created > 0 || updated > 0) && (failed > 0 || !errors.isEmpty());
        }

        public int getSuccessCount() {
            return created + updated;
        }

        public String getSummaryMessage() {
            if (isSuccessful()) {
                return String.format(
                        "Successfully imported %d matches (%d created, %d updated)",
                        getSuccessCount(), created, updated
                );
            } else if (isPartialSuccess()) {
                return String.format(
                        "Partial import: %d succeeded (%d created, %d updated), %d failed",
                        getSuccessCount(), created, updated, failed
                );
            } else {
                return String.format("Import failed: %d errors", errors.size());
            }
        }
    }
}
