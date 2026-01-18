package com.ligitabl.api.importer.model.entities;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.valueobjects.ExternalId;
import com.ligitabl.api.shared.Either;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExternalSeason {
    ExternalId id;
    String startDate;
    String endDate;
    Integer currentMatchday;

    public static Either<ImportError, ExternalSeason> create(
            Integer id, String startDate, String endDate, Integer currentMatchday) {

        return ExternalId.of(id).map(extId -> ExternalSeason.builder()
                .id(extId)
                .startDate(startDate)
                .endDate(endDate)
                .currentMatchday(currentMatchday)
                .build());
    }
}
