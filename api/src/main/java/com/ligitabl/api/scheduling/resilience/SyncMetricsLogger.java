package com.ligitabl.api.scheduling.resilience;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ligitabl.api.scheduling.syncmatches.MatchSyncResult;

/**
 * Sync Metrics Logger
 *
 * Provides simple log-based metrics for match synchronization.
 * Logs are structured for easy parsing and analysis.
 *
 * Usage:
 * - grep "SYNC_COMPLETED" logs/application.log | wc -l  # Count syncs
 * - grep "duration_ms" logs/application.log | awk '...'  # Average duration
 * - grep "API_ERROR" logs/application.log               # Error tracking
 */
@Component
public class SyncMetricsLogger {

    private static final Logger metricsLog = LoggerFactory.getLogger("METRICS");

    /**
     * Log successful sync metrics
     */
    public void logSyncCompleted(MatchSyncResult result, Duration duration) {
        metricsLog.info(
                "SYNC_COMPLETED | " + "processed={} | "
                        + "updated={} | "
                        + "finished={} | "
                        + "duration_ms={} | "
                        + "next_sync_min={} | "
                        + "all_complete={} | "
                        + "has_blocking={}",
                result.matchesProcessed(),
                result.matchesUpdated(),
                result.newlyFinishedMatches(),
                duration.toMillis(),
                result.nextSchedule().delay().toMinutes(),
                result.allMatchesComplete(),
                result.hasBlockingMatches());
    }

    /**
     * Log sync failure
     */
    public void logSyncFailed(String errorType, String message, Duration duration) {
        metricsLog.error(
                "SYNC_FAILED | " + "error_type={} | " + "message={} | " + "duration_ms={}",
                errorType,
                message,
                duration.toMillis());
    }

    /**
     * Log API error
     */
    public void logApiError(String endpoint, int statusCode, Duration duration) {
        metricsLog.error(
                "API_ERROR | " + "endpoint={} | " + "status={} | " + "duration_ms={}",
                endpoint,
                statusCode,
                duration.toMillis());
    }

    /**
     * Log finalization triggered
     */
    public void logFinalizationTriggered(Long roundId, int roundPosition, boolean blocked) {
        metricsLog.info(
                "FINALIZATION_TRIGGERED | " + "round_id={} | " + "position={} | " + "blocked={}",
                roundId,
                roundPosition,
                blocked);
    }

    /**
     * Log finalization completed
     */
    public void logFinalizationCompleted(
            Long roundId, int roundPosition, int submissions, int results, Duration duration) {
        metricsLog.info(
                "FINALIZATION_COMPLETED | " + "round_id={} | "
                        + "position={} | "
                        + "submissions={} | "
                        + "results={} | "
                        + "duration_ms={}",
                roundId,
                roundPosition,
                submissions,
                results,
                duration.toMillis());
    }

    /**
     * Log round advancement
     */
    public void logRoundAdvanced(String seasonId, int previousMatchday, int newMatchday) {
        metricsLog.info(
                "ROUND_ADVANCED | " + "season_id={} | " + "previous={} | " + "new={}",
                seasonId,
                previousMatchday,
                newMatchday);
    }

    /**
     * Log circuit breaker state change
     */
    public void logCircuitBreakerOpened(int failures) {
        metricsLog.error("CIRCUIT_BREAKER_OPENED | " + "failures={}", failures);
    }

    /**
     * Log circuit breaker recovery
     */
    public void logCircuitBreakerRecovered(int previousFailures) {
        metricsLog.info("CIRCUIT_BREAKER_RECOVERED | " + "previous_failures={}", previousFailures);
    }
}
