package com.ligitabl.api.importer.event;

import com.ligitabl.api.importer.model.Entities;
import com.ligitabl.api.importer.model.ImportError;
import com.ligitabl.api.importer.model.ValueObjects;

public interface ImportEventPublisher {

    void publishMatchCreated(Entities.MatchImportResult result);

    void publishMatchUpdated(Entities.MatchImportResult result);

    void publishMatchFailed(ValueObjects.ExternalId matchId, ImportError error);

    void publishImportCompleted(Entities.ImportSummary summary);
}
