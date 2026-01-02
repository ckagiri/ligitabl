package com.ligitabl.api.importer.model.entities;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.errors.ValidationError;
import com.ligitabl.api.importer.model.valueobjects.ExternalId;
import com.ligitabl.api.importer.model.valueobjects.KickOffTime;
import com.ligitabl.api.importer.model.valueobjects.MatchStatus;
import com.ligitabl.api.importer.model.valueobjects.Matchday;
import com.ligitabl.api.shared.Either;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

import static com.ligitabl.api.shared.Either.left;

@Value
@Builder
public class ExternalMatch {
    ExternalId id;
    KickOffTime kickOff;
    MatchStatus status;
    Matchday matchday;
    ExternalTeam homeTeam;
    ExternalTeam awayTeam;

    public static Either<ImportError, ExternalMatch> create(
            Integer id,
            OffsetDateTime utcDate,
            String status,
            Integer matchday,
            ExternalTeam homeTeam,
            ExternalTeam awayTeam) {

        if (homeTeam == null || awayTeam == null) {
            return left(ValidationError.of(
                    "Both home and away teams are required",
                    "teams"
            ));
        }

        return ExternalId.of(id)
                .flatMap(extId -> KickOffTime.of(utcDate)
                        .flatMap(kickOff -> MatchStatus.of(status)
                                .flatMap(matchStatus -> Matchday.of(matchday)
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
