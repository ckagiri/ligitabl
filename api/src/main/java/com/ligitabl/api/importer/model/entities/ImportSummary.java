package com.ligitabl.api.importer.model.entities;

import java.util.List;

import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.valueobjects.CompetitionCode;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImportSummary {
    CompetitionCode competition;
    String seasonName;
    int totalMatches;
    int created;
    int updated;
    int failed;
    List<ImportError> errors;

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
                    "Successfully imported %d matches (%d created, %d updated)", getSuccessCount(), created, updated);
        } else if (isPartialSuccess()) {
            return String.format(
                    "Partial import: %d succeeded (%d created, %d updated), %d failed",
                    getSuccessCount(), created, updated, failed);
        } else {
            return String.format("Import failed: %d errors", errors.size());
        }
    }
}
