package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TOutboxEvent.T_OUTBOX_EVENT;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;

import com.ligitabl.model.db.tables.records.OutboxEventRecord;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements OutboxRepo {
    private final DSLContext dsl;

    @Override
    public boolean save(OutboxEvent event) {
        // c_created_at is left to its DB default (now()), as is c_available_at unless the caller
        // asked for a delayed delivery (OutboxEvent.createAvailableAt).
        var insert = dsl.insertInto(T_OUTBOX_EVENT)
                .set(T_OUTBOX_EVENT.PK_ID, event.getId())
                .set(T_OUTBOX_EVENT.C_IDEMPOTENCY_KEY, event.getIdempotencyKey())
                .set(T_OUTBOX_EVENT.C_EVENT_TYPE, event.getEventType())
                .set(T_OUTBOX_EVENT.C_AGGREGATE_TYPE, event.getAggregateType())
                .set(T_OUTBOX_EVENT.C_AGGREGATE_ID, event.getAggregateId())
                .set(T_OUTBOX_EVENT.C_PAYLOAD, JSONB.valueOf(event.getPayload()))
                .set(T_OUTBOX_EVENT.C_STATUS, event.getStatus().name())
                .set(T_OUTBOX_EVENT.C_ATTEMPTS, event.getAttempts())
                .set(T_OUTBOX_EVENT.C_MAX_ATTEMPTS, event.getMaxAttempts());

        if (event.getAvailableAt() != null) {
            insert = insert.set(T_OUTBOX_EVENT.C_AVAILABLE_AT, toOffset(event.getAvailableAt()));
        }

        int inserted =
                insert.onConflict(T_OUTBOX_EVENT.C_IDEMPOTENCY_KEY).doNothing().execute();
        return inserted > 0;
    }

    @Override
    public Optional<OutboxEvent> findByIdempotencyKey(String key) {
        return dsl.selectFrom(T_OUTBOX_EVENT)
                .where(T_OUTBOX_EVENT.C_IDEMPOTENCY_KEY.eq(key))
                .fetchOptional(this::map);
    }

    @Override
    public List<OutboxEvent> claimBatchForProcessing(int batchSize) {
        // Single atomic statement: the subquery locks eligible rows (SKIP LOCKED
        // keeps concurrent claimers disjoint) and the update flips them to
        // PROCESSING before any other claimer can see them. available_at is
        // re-stamped to now() to record the claim time for resetStuckProcessing.
        return dsl.update(T_OUTBOX_EVENT)
                .set(T_OUTBOX_EVENT.C_STATUS, OutboxEvent.Status.PROCESSING.name())
                .set(T_OUTBOX_EVENT.C_ATTEMPTS, T_OUTBOX_EVENT.C_ATTEMPTS.plus(1))
                .set(T_OUTBOX_EVENT.C_AVAILABLE_AT, DSL.currentOffsetDateTime())
                .where(T_OUTBOX_EVENT.PK_ID.in(dsl.select(T_OUTBOX_EVENT.PK_ID)
                        .from(T_OUTBOX_EVENT)
                        .where(T_OUTBOX_EVENT.C_STATUS.in(
                                OutboxEvent.Status.PENDING.name(), OutboxEvent.Status.FAILED.name()))
                        .and(T_OUTBOX_EVENT.C_AVAILABLE_AT.le(DSL.currentOffsetDateTime()))
                        .orderBy(T_OUTBOX_EVENT.C_AVAILABLE_AT.asc())
                        .limit(batchSize)
                        .forUpdate()
                        .skipLocked()))
                .returning()
                .fetch()
                .map(this::map);
    }

    @Override
    public void markSent(UUID id) {
        dsl.update(T_OUTBOX_EVENT)
                .set(T_OUTBOX_EVENT.C_STATUS, OutboxEvent.Status.SENT.name())
                .set(T_OUTBOX_EVENT.C_PROCESSED_AT, DSL.currentOffsetDateTime())
                .where(T_OUTBOX_EVENT.PK_ID.eq(id))
                .execute();
    }

    @Override
    public void markFailed(UUID id, String error, Instant nextAvailableAt) {
        dsl.update(T_OUTBOX_EVENT)
                .set(T_OUTBOX_EVENT.C_STATUS, OutboxEvent.Status.FAILED.name())
                .set(T_OUTBOX_EVENT.C_LAST_ERROR, error)
                .set(T_OUTBOX_EVENT.C_AVAILABLE_AT, toOffset(nextAvailableAt))
                .where(T_OUTBOX_EVENT.PK_ID.eq(id))
                .execute();
    }

    @Override
    public void markDeadLetter(UUID id, String error) {
        dsl.update(T_OUTBOX_EVENT)
                .set(T_OUTBOX_EVENT.C_STATUS, OutboxEvent.Status.DEAD_LETTER.name())
                .set(T_OUTBOX_EVENT.C_LAST_ERROR, error)
                .set(T_OUTBOX_EVENT.C_PROCESSED_AT, DSL.currentOffsetDateTime())
                .where(T_OUTBOX_EVENT.PK_ID.eq(id))
                .execute();
    }

    @Override
    public int resetStuckProcessing(Instant olderThan) {
        return dsl.update(T_OUTBOX_EVENT)
                .set(T_OUTBOX_EVENT.C_STATUS, OutboxEvent.Status.FAILED.name())
                .set(T_OUTBOX_EVENT.C_AVAILABLE_AT, DSL.currentOffsetDateTime())
                .where(T_OUTBOX_EVENT.C_STATUS.eq(OutboxEvent.Status.PROCESSING.name()))
                .and(T_OUTBOX_EVENT.C_AVAILABLE_AT.lt(toOffset(olderThan)))
                .execute();
    }

    @Override
    public boolean existsSentEventsOfTypeSince(String eventType, Instant since) {
        return dsl.fetchExists(dsl.selectOne()
                .from(T_OUTBOX_EVENT)
                .where(T_OUTBOX_EVENT.C_EVENT_TYPE.eq(eventType))
                .and(T_OUTBOX_EVENT.C_STATUS.eq(OutboxEvent.Status.SENT.name()))
                .and(T_OUTBOX_EVENT.C_PROCESSED_AT.ge(toOffset(since))));
    }

    @Override
    public boolean existsEventsOfTypeCreatedSince(String eventType, Instant since) {
        return dsl.fetchExists(dsl.selectOne()
                .from(T_OUTBOX_EVENT)
                .where(T_OUTBOX_EVENT.C_EVENT_TYPE.eq(eventType))
                .and(T_OUTBOX_EVENT.C_CREATED_AT.ge(toOffset(since))));
    }

    private OutboxEvent map(OutboxEventRecord record) {
        return OutboxEvent.builder()
                .id(record.getId())
                .idempotencyKey(record.getIdempotencyKey())
                .eventType(record.getEventType())
                .aggregateType(record.getAggregateType())
                .aggregateId(record.getAggregateId())
                .payload(
                        record.getPayload() == null ? null : record.getPayload().data())
                .status(OutboxEvent.Status.valueOf(record.getStatus()))
                .attempts(record.getAttempts())
                .maxAttempts(record.getMaxAttempts())
                .lastError(record.getLastError())
                .availableAt(toInstant(record.getAvailableAt()))
                .createdAt(toInstant(record.getCreatedAt()))
                .processedAt(toInstant(record.getProcessedAt()))
                .build();
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
