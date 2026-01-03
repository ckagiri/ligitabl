package com.ligitabl.api.usecases.importer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ligitabl.api.config.WorkflowConfiguration;
import com.ligitabl.api.importer.model.entities.ImportSummary;
import com.ligitabl.api.importer.model.errors.ApiError;
import com.ligitabl.api.importer.model.errors.DatabaseError;
import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.errors.MappingError;
import com.ligitabl.api.importer.model.errors.ValidationError;
import com.ligitabl.api.importer.model.valueobjects.CompetitionCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "workflow", name = "run", havingValue = "true")
public class ImportWorkflowRunner implements ApplicationRunner {

    private final ImportMatchesUseCase useCase;
    private final WorkflowConfiguration config;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String competitionCodeStr = config.getCompetition();
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  Match Import Workflow - {}", String.format("%-36s", competitionCodeStr) + "║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        // Validate and create competition code (value object with validation)
        var codeResult = CompetitionCode.of(competitionCodeStr);
        if (codeResult.isLeft()) {
            log.error("Invalid competition code: {}", competitionCodeStr);
            log.error("Error: {}", codeResult.getLeft().message());
            if (config.isExitAfter()) {
                System.exit(1);
            }
            return;
        }

        CompetitionCode code = codeResult.get();

        // Execute the use case
        var result = useCase.execute(code);

        // Handle the result using fold (functional error handling)
        result.fold(error -> handleError(error), summary -> handleSuccess(summary));
    }

    /**
     * Handle import error - called when result.isLeft()
     */
    private Void handleError(ImportError error) {
        log.error("╔══════════════════════════════════════════════════════════╗");
        log.error("║  Import FAILED                                           ║");
        log.error("╚══════════════════════════════════════════════════════════╝");
        log.error("Error Type:    {}", error.code());
        log.error("Error Message: {}", error.message());
        log.error("Display:       {}", error.toDisplayMessage());

        // Log specific error details based on type
        switch (error) {
            case ApiError e -> log.error("API Error: status={}, message={}", e.getStatusCode(), e.message());

            case ValidationError e -> log.error("Validation Error: field={}, message={}", e.getField(), e.message());

            case DatabaseError e -> log.error("Database Error: entity={}, message={}", e.getEntity(), e.message());

            case MappingError e -> log.error("Mapping Error: field={}, message={}", e.getSourceField(), e.message());
        }

        if (config.isExitAfter()) {
            System.exit(1);
        }
        return null;
    }

    /**
     * Handle import success - called when result.isRight()
     */
    private Void handleSuccess(ImportSummary summary) {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  Import Summary                                          ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info(
                "║  Competition:  {}",
                String.format("%-44s", summary.getCompetition().getValue()) + "║");
        log.info("║  Season:       {}", String.format("%-44s", summary.getSeasonName()) + "║");
        log.info("║  Total:        {}", String.format("%-44s", summary.getTotalMatches()) + "║");
        log.info("║  Created:      {}", String.format("%-44s", summary.getCreated()) + "║");
        log.info("║  Updated:      {}", String.format("%-44s", summary.getUpdated()) + "║");
        log.info("║  Failed:       {}", String.format("%-44s", summary.getFailed()) + "║");
        log.info("╠══════════════════════════════════════════════════════════╣");

        if (summary.isSuccessful()) {
            log.info("║  Status:       ✅ SUCCESS                                 ║");
        } else if (summary.isPartialSuccess()) {
            log.warn("║  Status:       ⚠️  PARTIAL SUCCESS                        ║");
        } else {
            log.error("║  Status:       ❌ FAILED                                  ║");
        }

        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  {}", String.format("%-56s", summary.getSummaryMessage()) + "║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        // Log errors if any
        if (!summary.getErrors().isEmpty()) {
            log.warn("Import completed with {} error(s):", summary.getErrors().size());
            for (int i = 0; i < Math.min(5, summary.getErrors().size()); i++) {
                log.warn("  {}. {}", i + 1, summary.getErrors().get(i).message());
            }
            if (summary.getErrors().size() > 5) {
                log.warn("  ... and {} more errors", summary.getErrors().size() - 5);
            }
        }

        if (config.isExitAfter()) {
            int exitCode = summary.isSuccessful() ? 0 : (summary.isPartialSuccess() ? 2 : 1);
            System.exit(exitCode);
        }
        return null;
    }
}
