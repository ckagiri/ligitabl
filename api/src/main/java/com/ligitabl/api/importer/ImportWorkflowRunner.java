package com.ligitabl.api.importer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Script-style entrypoint for importing matches for a competition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "workflow", name = "run", havingValue = "true")
public class ImportWorkflowRunner implements ApplicationRunner {

    private final ImportWorkflowService workflowService;
    private final WorkflowConfiguration config;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║        Match Import Workflow (Script Mode)                ║");
        log.info("╚═══════════════════════════════════════════════════════════╝");

        String competitionCode = config.getCompetition();
        log.info("Running import workflow for competition: {}", competitionCode);

        try {
            ImportWorkflowService.WorkflowResult result = workflowService.importMatchesForCompetition(competitionCode);

            log.info("────────────────────────────────────────────────────────────");
            log.info("Competition: {}", result.getCompetitionCode());
            log.info("Season:      {}", result.getSeasonName());
            log.info("Matches:     {}", result.getMatchesCreated());
            log.info("Success:     {}", result.isSuccess());
            log.info("Message:     {}", result.getMessage());
            log.info("────────────────────────────────────────────────────────────");

            if (config.isExitAfter()) {
                System.exit(result.isSuccess() ? 0 : 1);
            }
        } catch (Exception e) {
            log.error("Workflow FAILED for {}", competitionCode, e);
            if (config.isExitAfter()) {
                System.exit(1);
            }
            throw e;
        }
    }
}
