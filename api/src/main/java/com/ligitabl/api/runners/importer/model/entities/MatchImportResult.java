package com.ligitabl.api.runners.importer.model.entities;

import com.ligitabl.api.runners.importer.model.valueobjects.ExternalId;
import com.ligitabl.api.runners.importer.model.valueobjects.MatchSlug;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MatchImportResult {
    ExternalId matchId;
    boolean created;
    boolean updated;
    MatchSlug slug;

    public static MatchImportResult created(ExternalId matchId, MatchSlug slug) {
        return MatchImportResult.builder()
                .matchId(matchId)
                .created(true)
                .updated(false)
                .slug(slug)
                .build();
    }

    public static MatchImportResult updated(ExternalId matchId, MatchSlug slug) {
        return MatchImportResult.builder()
                .matchId(matchId)
                .created(false)
                .updated(true)
                .slug(slug)
                .build();
    }
}
