package com.ligitabl.api.importer.event;

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
