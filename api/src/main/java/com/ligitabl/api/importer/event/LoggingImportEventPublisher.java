package com.ligitabl.api.importer.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingImportEventPublisher implements ImportEventPublisher {

    @Override
    public void publishMatchCreated(MatchImportResult result) {
        log.info("Match created: clientId={}, slug={}",
                result.getMatchId().getValue(),
                result.getSlug().getValue());

        log.debug("Match creation details: {}", result);
    }

    @Override
    public void publishMatchUpdated(MatchImportResult result) {
        log.info("Match updated: clientId={}, slug={}",
                result.getMatchId().getValue(),
                result.getSlug().getValue());

        log.debug("Match update details: {}", result);
    }

    @Override
    public void publishMatchFailed(ExternalId matchId, ImportError error) {
        log.warn("Match import failed: clientId={}, error={}, message={}",
                matchId.getValue(),
                error.code(),
                error.message());

        log.debug("Match failure details: matchId={}, error={}", matchId, error);
    }

    @Override
    public void publishImportCompleted(ImportSummary summary) {
        if (summary.isSuccessful()) {
            log.info("Import completed successfully: competition={}, created={}, updated={}",
                    summary.getCompetition().getValue(),
                    summary.getCreated(),
                    summary.getUpdated());
        } else if (summary.isPartialSuccess()) {
            log.warn("Import partially completed: competition={}, succeeded={}, failed={}",
                    summary.getCompetition().getValue(),
                    summary.getSuccessCount(),
                    summary.getFailed());
        } else {
            log.error("Import failed: competition={}, errors={}",
                    summary.getCompetition().getValue(),
                    summary.getErrors().size());
        }

        log.debug("Import summary: {}", summary);
    }
}
