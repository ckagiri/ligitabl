package com.ligitabl.api.importer.model.entities;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.importer.model.valueobjects.ExternalId;
import com.ligitabl.api.shared.Either;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExternalCompetition {
    ExternalId id;
    String name;
    CompetitionCode code;
    ExternalSeason currentSeason;

    public static Either<ImportError, ExternalCompetition> create(
            Integer id, String name, String code, ExternalSeason currentSeason) {

        return ExternalId.of(id).flatMap(extId -> CompetitionCode.of(code).map(compCode -> ExternalCompetition.builder()
                .id(extId)
                .name(name)
                .code(compCode)
                .currentSeason(currentSeason)
                .build()));
    }
}
