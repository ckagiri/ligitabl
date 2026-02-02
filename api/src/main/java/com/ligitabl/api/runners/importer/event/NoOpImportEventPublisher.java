package com.ligitabl.api.runners.importer.event;

import com.ligitabl.api.runners.importer.model.entities.ImportSummary;
import com.ligitabl.api.runners.importer.model.entities.MatchImportResult;
import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.valueobjects.ExternalId;

public class NoOpImportEventPublisher implements ImportEventPublisher {

    @Override
    public void publishMatchCreated(MatchImportResult result) {
        // No-op
    }

    @Override
    public void publishMatchUpdated(MatchImportResult result) {
        // No-op
    }

    @Override
    public void publishMatchFailed(ExternalId matchId, ImportError error) {
        // No-op
    }

    @Override
    public void publishImportCompleted(ImportSummary summary) {
        // No-op
    }
}
