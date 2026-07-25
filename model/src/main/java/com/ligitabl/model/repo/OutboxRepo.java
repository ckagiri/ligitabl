package com.ligitabl.model.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.OutboxEvent;

/**
 * Transactional outbox store.
 *
 * <p>Lifecycle: PENDING → PROCESSING → SENT, or PROCESSING → FAILED (retried
 * after backoff) → … → DEAD_LETTER once attempts are exhausted.
 */
public interface OutboxRepo {

    /**
     * Idempotent insert: a row whose idempotency key already exists is silently
     * skipped (ON CONFLICT DO NOTHING).
     *
     * @return true if a new row was inserted, false if the key already existed
     */
    boolean save(OutboxEvent event);

    Optional<OutboxEvent> findByIdempotencyKey(String key);

    /**
     * Atomically claims up to {@code batchSize} due PENDING/FAILED events:
     * marks them PROCESSING, increments their attempt counter and stamps
     * {@code available_at = now()} (recording claim time for stuck-row
     * recovery), using FOR UPDATE SKIP LOCKED so concurrent claimers get
     * disjoint batches. Eligibility is {@code available_at <= now()}
     * (database clock), oldest first.
     */
    List<OutboxEvent> claimBatchForProcessing(int batchSize);

    void markSent(UUID id);

    /** Records the failure and schedules the next retry. */
    void markFailed(UUID id, String error, Instant nextAvailableAt);

    /** Terminal failure; the event is never retried automatically. */
    void markDeadLetter(UUID id, String error);

    /**
     * Crash recovery: PROCESSING rows whose claim timestamp ({@code available_at},
     * stamped at claim time) is before {@code olderThan} were claimed by a
     * process that died before resolving them. They are put back to FAILED with
     * immediate availability so they re-enter the retry cycle.
     *
     * @return number of rows reset
     */
    int resetStuckProcessing(Instant olderThan);

    /**
     * True if any event of {@code eventType} reached SENT status at or after
     * {@code since}.
     */
    boolean existsSentEventsOfTypeSince(String eventType, Instant since);
}
