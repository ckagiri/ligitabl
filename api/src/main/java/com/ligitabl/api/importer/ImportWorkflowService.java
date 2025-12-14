package com.ligitabl.api.importer;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * High-level workflow service that orchestrates a full import for a competition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportWorkflowService {

    private final MatchImportService matchImportService;

    @Transactional
    public WorkflowResult importMatchesForCompetition(String competitionCode) {
        WorkflowResult result = new WorkflowResult();
        result.setCompetitionCode(competitionCode);

        log.info("Starting import for competition {} via workflow service", competitionCode);

        MatchImportService.ImportResult importResult =
                matchImportService.importMatchesForCompetition(competitionCode);

        result.setMatchesCreated(importResult.getCreated());
        result.setSuccess(importResult.isSuccess());
        result.setMessage(importResult.getMessage());
        result.setSeasonName(importResult.getSeasonName());

        return result;
    }

    @Data
    public static class WorkflowResult {
        private boolean success;
        private String message;
        private String competitionCode;
        private String seasonName;
        private long matchesCreated;
    }
}
