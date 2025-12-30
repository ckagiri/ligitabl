package com.ligitabl.api.scheduling.resilience;

import com.ligitabl.api.notification.AdminNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Circuit Breaker for Match Sync
 *
 * Prevents runaway failures by stopping sync attempts after N consecutive failures.
 * Automatically recovers after a wait period.
 *
 * States:
 * - CLOSED: Normal operation
 * - OPEN: Too many failures, blocking requests
 * - HALF_OPEN: Testing if service recovered
 */
@Component
public class MatchSyncCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(MatchSyncCircuitBreaker.class);

    private static final int FAILURE_THRESHOLD = 10;
    private static final Duration RECOVERY_WAIT = Duration.ofHours(1);

    private final AdminNotificationService notificationService;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Instant openedAt = null;

    public MatchSyncCircuitBreaker(AdminNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Too many failures, stop trying
        HALF_OPEN  // Testing if service recovered
    }

    /**
     * Get current circuit breaker state
     */
    public State getState() {
        if (openedAt == null) {
            return State.CLOSED;
        }

        // Check if recovery period has passed
        if (Instant.now().isAfter(openedAt.plus(RECOVERY_WAIT))) {
            log.info("Circuit breaker recovery period elapsed, entering HALF_OPEN state");
            return State.HALF_OPEN; // Try again
        }

        return State.OPEN;
    }

    /**
     * Check if request should be allowed
     */
    public boolean allowRequest() {
        State state = getState();

        if (state == State.OPEN) {
            log.warn("Circuit breaker OPEN - blocking sync request (failures: {}, opened: {})",
                    consecutiveFailures.get(),
                    openedAt);
            return false;
        }

        if (state == State.HALF_OPEN) {
            log.info("Circuit breaker HALF_OPEN - allowing test request");
        }

        return true;
    }

    /**
     * Record successful sync
     */
    public void recordSuccess() {
        int previousFailures = consecutiveFailures.get();

        if (openedAt != null) {
            log.info("Circuit breaker recovered after {} failures - returning to CLOSED state",
                    previousFailures);

            notificationService.notifyCircuitBreakerRecovered(previousFailures);
        }

        consecutiveFailures.set(0);
        openedAt = null;
    }

    /**
     * Record failed sync
     */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();

        log.warn("Match sync failure recorded: {}/{}", failures, FAILURE_THRESHOLD);

        // Open circuit breaker if threshold reached
        if (failures >= FAILURE_THRESHOLD && openedAt == null) {
            openedAt = Instant.now();

            log.error("Circuit breaker OPENED after {} consecutive failures. " +
                            "Will retry after {} hour(s)",
                    failures,
                    RECOVERY_WAIT.toHours());

            notificationService.notifyCircuitBreakerOpened(
                    failures,
                    RECOVERY_WAIT.toHours()
            );
        }
    }

    /**
     * Get current failure count
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Get time when circuit was opened (null if closed)
     */
    public Instant getOpenedAt() {
        return openedAt;
    }

    /**
     * Manually reset circuit breaker (admin override)
     */
    public void reset() {
        log.warn("Circuit breaker manually reset");
        consecutiveFailures.set(0);
        openedAt = null;
    }
}
