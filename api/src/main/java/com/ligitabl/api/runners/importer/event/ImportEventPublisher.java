package com.ligitabl.api.runners.importer.event;

import com.ligitabl.api.runners.importer.model.entities.ImportSummary;
import com.ligitabl.api.runners.importer.model.entities.MatchImportResult;
import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.valueobjects.ExternalId;

public interface ImportEventPublisher {

    void publishMatchCreated(MatchImportResult result);

    void publishMatchUpdated(MatchImportResult result);

    void publishMatchFailed(ExternalId matchId, ImportError error);

    void publishImportCompleted(ImportSummary summary);
}
