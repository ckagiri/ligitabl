package com.ligitabl.model.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * Transactional outbox event. A row is written in the same transaction as the
 * domain change it announces (e.g. round finalization) and drained
 * asynchronously by the relay job.
 *
 * <p>Attempt counting: {@code attempts} is the number of delivery attempts
 * started so far — the claim query increments it, so a freshly claimed event
 * already counts the in-flight attempt. On failure the relay dead-letters when
 * {@link #hasExceededMaxAttempts()} is true, otherwise reschedules at
 * {@link #nextAvailableAt(Instant)}.
 */
@Value
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class OutboxEvent {

    public enum Status {
        PENDING,
        PROCESSING,
        SENT,
        FAILED,
        DEAD_LETTER
    }

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    UUID id;

    /** Uniquely identifies the fact being announced; enforces at-most-once enqueue. */
    String idempotencyKey;

    String eventType;

    String aggregateType;

    String aggregateId;

    /** JSON document; serialization/deserialization is the caller's concern. */
    String payload;

    Status status;

    int attempts;

    int maxAttempts;

    String lastError;

    /**
     * Earliest time the event is eligible for (re-)delivery. Null on a freshly built event means
     * "as soon as possible" — the insert leaves the column to its DB default of {@code now()}.
     * Set it via {@link #createAvailableAt} to hold an event back until a chosen time.
     */
    Instant availableAt;

    /** Set by the DB on insert. */
    Instant createdAt;

    /** When the event reached a terminal state (SENT or DEAD_LETTER). */
    Instant processedAt;

    public static OutboxEvent create(
            String idempotencyKey, String eventType, String aggregateType, String aggregateId, String payload) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payload(payload)
                .status(Status.PENDING)
                .attempts(0)
                .maxAttempts(DEFAULT_MAX_ATTEMPTS)
                .build();
    }

    /**
     * An event that must not be delivered before {@code availableAt} — the relay's claim query
     * only considers rows whose {@code available_at} has passed, so a future value simply parks
     * the row in PENDING until then.
     *
     * <p>Use this for deliberate scheduling ("send this a day from now"), not for retry backoff;
     * failed events are rescheduled through {@link #nextAvailableAt(Instant)} instead.
     */
    public static OutboxEvent createAvailableAt(
            String idempotencyKey,
            String eventType,
            String aggregateType,
            String aggregateId,
            String payload,
            Instant availableAt) {
        return create(idempotencyKey, eventType, aggregateType, aggregateId, payload).toBuilder()
                .availableAt(availableAt)
                .build();
    }

    public boolean hasExceededMaxAttempts() {
        return attempts >= maxAttempts;
    }

    /**
     * Backoff schedule keyed by attempts already made: 1m, 5m, 15m, 30m, then
     * 60m for every attempt after that.
     */
    public Instant nextAvailableAt(Instant now) {
        return now.plus(backoffForAttempt(attempts));
    }

    private static Duration backoffForAttempt(int attempts) {
        return switch (Math.max(attempts, 1)) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(15);
            case 4 -> Duration.ofMinutes(30);
            default -> Duration.ofMinutes(60);
        };
    }
}
