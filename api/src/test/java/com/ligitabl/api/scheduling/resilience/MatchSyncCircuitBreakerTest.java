package com.ligitabl.api.scheduling.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.scheduling.resilience.MatchSyncCircuitBreaker.State;

@ExtendWith(MockitoExtension.class)
class MatchSyncCircuitBreakerTest {

    private static final int FAILURE_THRESHOLD = 10;
    private static final Duration RECOVERY_WAIT = Duration.ofMinutes(30);

    @Mock
    private AdminNotificationService notificationService;

    private MutableClock clock;
    private MatchSyncCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        circuitBreaker = new MatchSyncCircuitBreaker(notificationService, clock);
    }

    @Test
    void staysClosedBelowFailureThreshold() {
        recordFailures(FAILURE_THRESHOLD - 1);

        assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
        assertThat(circuitBreaker.allowRequest()).isTrue();
        verify(notificationService, never()).notifyCircuitBreakerOpened(anyInt(), anyLong());
    }

    @Test
    void opensAfterThresholdFailures_andNotifiesAdminOnce() {
        recordFailures(FAILURE_THRESHOLD);

        assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(circuitBreaker.allowRequest()).isFalse();
        verify(notificationService).notifyCircuitBreakerOpened(FAILURE_THRESHOLD, RECOVERY_WAIT.toMinutes());
    }

    @Test
    void entersHalfOpenAfterRecoveryWait_andAllowsTestRequest() {
        recordFailures(FAILURE_THRESHOLD);

        clock.advance(RECOVERY_WAIT.plusMinutes(1));

        assertThat(circuitBreaker.getState()).isEqualTo(State.HALF_OPEN);
        assertThat(circuitBreaker.allowRequest()).isTrue();
    }

    @Test
    void reopensWhenHalfOpenTestRequestFails() {
        recordFailures(FAILURE_THRESHOLD);
        clock.advance(RECOVERY_WAIT.plusMinutes(1));
        assertThat(circuitBreaker.getState()).isEqualTo(State.HALF_OPEN);

        circuitBreaker.recordFailure();

        assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);
        assertThat(circuitBreaker.allowRequest()).isFalse();

        // Another full recovery wait is required before the next test request
        clock.advance(RECOVERY_WAIT.plusMinutes(1));
        assertThat(circuitBreaker.getState()).isEqualTo(State.HALF_OPEN);

        // Re-opening does not send a second admin notification
        verify(notificationService, times(1)).notifyCircuitBreakerOpened(anyInt(), anyLong());
    }

    @Test
    void closesAndNotifiesRecovery_onSuccessAfterOpen() {
        recordFailures(FAILURE_THRESHOLD);
        clock.advance(RECOVERY_WAIT.plusMinutes(1));

        circuitBreaker.recordSuccess();

        assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
        assertThat(circuitBreaker.getConsecutiveFailures()).isZero();
        verify(notificationService).notifyCircuitBreakerRecovered(FAILURE_THRESHOLD);
    }

    @Test
    void successWhileClosedDoesNotNotify() {
        circuitBreaker.recordSuccess();

        assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
        verify(notificationService, never()).notifyCircuitBreakerRecovered(anyInt());
    }

    @Test
    void remainingRecoveryTime_isZeroWhenClosed_andCountsDownWhenOpen() {
        assertThat(circuitBreaker.getRemainingRecoveryTime()).isEqualTo(Duration.ZERO);

        recordFailures(FAILURE_THRESHOLD);
        assertThat(circuitBreaker.getRemainingRecoveryTime()).isEqualTo(RECOVERY_WAIT);

        clock.advance(Duration.ofMinutes(20));
        assertThat(circuitBreaker.getRemainingRecoveryTime()).isEqualTo(RECOVERY_WAIT.minusMinutes(20));

        clock.advance(RECOVERY_WAIT);
        assertThat(circuitBreaker.getRemainingRecoveryTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    void manualResetClosesBreaker() {
        recordFailures(FAILURE_THRESHOLD);

        circuitBreaker.reset();

        assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
        assertThat(circuitBreaker.getConsecutiveFailures()).isZero();
        assertThat(circuitBreaker.allowRequest()).isTrue();
    }

    private void recordFailures(int count) {
        for (int i = 0; i < count; i++) {
            circuitBreaker.recordFailure();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
