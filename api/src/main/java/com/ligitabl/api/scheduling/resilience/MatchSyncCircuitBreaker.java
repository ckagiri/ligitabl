package com.ligitabl.api.scheduling.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.AdminNotificationService;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class MatchSyncCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 10;
    private static final Duration RECOVERY_WAIT = Duration.ofMinutes(30);

    private final AdminNotificationService notificationService;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Instant openedAt = null;

    @Autowired
    public MatchSyncCircuitBreaker(AdminNotificationService notificationService) {
        this(notificationService, Clock.systemUTC());
    }

    public MatchSyncCircuitBreaker(AdminNotificationService notificationService, Clock clock) {
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public enum State {
        CLOSED, // Normal operation
        OPEN, // Too many failures, stop trying
        HALF_OPEN // Testing if service recovered
    }

    /**
     * Get current circuit breaker state
     */
    public State getState() {
        if (openedAt == null) {
            return State.CLOSED;
        }

        // Check if recovery period has passed
        if (Instant.now(clock).isAfter(openedAt.plus(RECOVERY_WAIT))) {
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
            log.warn(
                    "Circuit breaker OPEN - blocking sync request (failures: {}, opened: {})",
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
            log.info("Circuit breaker recovered after {} failures - returning to CLOSED state", previousFailures);

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

        // HALF_OPEN test request failed - re-open for another recovery period
        if (openedAt != null) {
            if (getState() == State.HALF_OPEN) {
                openedAt = Instant.now(clock);

                log.error(
                        "Circuit breaker RE-OPENED after failed test request ({} consecutive failures). "
                                + "Will retry after {} minute(s)",
                        failures,
                        RECOVERY_WAIT.toMinutes());
            }
            return;
        }

        // Open circuit breaker if threshold reached
        if (failures >= FAILURE_THRESHOLD) {
            openedAt = Instant.now(clock);

            log.error(
                    "Circuit breaker OPENED after {} consecutive failures. " + "Will retry after {} minute(s)",
                    failures,
                    RECOVERY_WAIT.toMinutes());

            notificationService.notifyCircuitBreakerOpened(failures, RECOVERY_WAIT.toMinutes());
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
     * Time remaining until the breaker will allow a test request.
     * Duration.ZERO if the breaker is closed or already half-open.
     */
    public Duration getRemainingRecoveryTime() {
        Instant opened = openedAt;
        if (opened == null) {
            return Duration.ZERO;
        }

        Duration remaining = Duration.between(Instant.now(clock), opened.plus(RECOVERY_WAIT));
        return remaining.isNegative() ? Duration.ZERO : remaining;
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
