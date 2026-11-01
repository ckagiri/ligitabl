package com.ligitabl.api.usecase.match.syncmatches;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.usecase.shared.standings.RoundStandingsCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recalculates standings on its own thread pool and transaction, so the sync loop never waits.
 * Failures are logged and never propagate.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncStandingsService {

    private final RoundStandingsCalculator standingsCalculator;

    @Async("asyncStandingsExecutor")
    @Transactional
    public void recalculateAsync(UUID seasonId, int roundPosition) {
        try {
            log.info("Starting async standings recalculation: season={}, round={}", seasonId, roundPosition);

            long startTime = System.currentTimeMillis();

            standingsCalculator.calculateAndPersist(seasonId, roundPosition);

            long duration = System.currentTimeMillis() - startTime;

            log.info("Async standings recalculation completed in {}ms", duration);

        } catch (Exception e) {
            log.error("Async standings recalculation failed for season={}, round={}", seasonId, roundPosition, e);

            // Don't rethrow - we don't want to block the sync
            // The next sync will try again
        }
    }

    /**
     * Recalculate standings synchronously (for testing or admin operations)
     */
    @Transactional
    public void recalculateSync(UUID seasonId, int roundPosition) {
        log.info("Starting sync standings recalculation: season={}, round={}", seasonId, roundPosition);

        try {
            standingsCalculator.calculateAndPersist(seasonId, roundPosition);

            log.info("Sync standings recalculation completed");

        } catch (Exception e) {
            log.error("Sync standings recalculation failed", e);
            throw e; // Propagate in sync mode
        }
    }
}
