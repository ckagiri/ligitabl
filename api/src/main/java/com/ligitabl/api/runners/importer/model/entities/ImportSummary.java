package com.ligitabl.api.runners.importer.model.entities;

import java.util.List;

import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.valueobjects.CompetitionCode;

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
    int unchanged;
    int failed;
    Integer currentRoundPosition;
    boolean currentRoundUpdated;
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
                    "Successfully imported %d matches (%d created, %d updated, %d unchanged)",
                    getSuccessCount(), created, updated, unchanged);
        } else if (isPartialSuccess()) {
            return String.format(
                    "Partial import: %d succeeded (%d created, %d updated, %d unchanged), %d failed",
                    getSuccessCount(), created, updated, unchanged, failed);
        } else {
            return String.format("Import failed: %d errors", errors.size());
        }
    }
}
