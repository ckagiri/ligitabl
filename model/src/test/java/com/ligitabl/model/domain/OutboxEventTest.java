package com.ligitabl.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    private static OutboxEvent eventWithAttempts(int attempts) {
        return OutboxEvent.create("key", "ROUND_RESULTS", "round", "22", "{}").toBuilder()
                .attempts(attempts)
                .build();
    }

    @Test
    void createSetsDefaults() {
        OutboxEvent event = OutboxEvent.create("round-results:s1:22:u1", "ROUND_RESULTS", "round", "22", "{\"a\":1}");

        assertThat(event.getId()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getMaxAttempts()).isEqualTo(OutboxEvent.DEFAULT_MAX_ATTEMPTS);
    }

    @Test
    void backoffFollowsSchedule() {
        assertThat(eventWithAttempts(1).nextAvailableAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(eventWithAttempts(2).nextAvailableAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(eventWithAttempts(3).nextAvailableAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(eventWithAttempts(4).nextAvailableAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(eventWithAttempts(5).nextAvailableAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(eventWithAttempts(9).nextAvailableAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
    }

    @Test
    void zeroAttemptsFallsBackToFirstDelay() {
        assertThat(eventWithAttempts(0).nextAvailableAt(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void hasExceededMaxAttemptsBoundary() {
        assertThat(eventWithAttempts(4).hasExceededMaxAttempts()).isFalse();
        assertThat(eventWithAttempts(5).hasExceededMaxAttempts()).isTrue();
        assertThat(eventWithAttempts(6).hasExceededMaxAttempts()).isTrue();
    }
}
