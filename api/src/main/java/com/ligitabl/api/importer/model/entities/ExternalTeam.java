package com.ligitabl.api.importer.model.entities;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.valueobjects.ExternalId;
import com.ligitabl.api.importer.model.valueobjects.TeamTla;
import com.ligitabl.api.shared.Either;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExternalTeam {
    ExternalId id;
    String name;
    TeamTla tla;

    public static Either<ImportError, ExternalTeam> create(
            Integer id,
            String name,
            String tla) {

        return ExternalId.of(id)
                .flatMap(extId -> TeamTla.of(tla)
                        .map(teamTla -> ExternalTeam.builder()
                                .id(extId)
                                .name(name)
                                .tla(teamTla)
                                .build()));
    }
}
