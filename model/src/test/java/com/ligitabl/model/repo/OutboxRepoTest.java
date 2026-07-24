package com.ligitabl.model.repo;

import static com.ligitabl.model.db.tables.TOutboxEvent.T_OUTBOX_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.infra.OutboxPersistenceAdapter;

@Tag("integration")
class OutboxRepoTest {

    private static Connection jdbc;
    private static DSLContext dsl;
    private static OutboxRepo repo;

    @BeforeAll
    static void setup() throws Exception {
        jdbc = TestDbConnections.open();
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new OutboxPersistenceAdapter(dsl);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    @BeforeEach
    void cleanTable() {
        dsl.deleteFrom(T_OUTBOX_EVENT).execute();
    }

    private static OutboxEvent newEvent(String key) {
        return OutboxEvent.create(key, "ROUND_RESULTS", "round", "22", "{\"round\":22}");
    }

    @Test
    void saveInsertsAndDuplicateKeyIsSilentNoOp() {
        OutboxEvent first = newEvent("round-results:s:22:u1");
        OutboxEvent duplicate = newEvent("round-results:s:22:u1");

        assertThat(repo.save(first)).isTrue();
        assertThat(repo.save(duplicate)).isFalse();

        assertThat(dsl.fetchCount(T_OUTBOX_EVENT)).isEqualTo(1);
        Optional<OutboxEvent> stored = repo.findByIdempotencyKey("round-results:s:22:u1");
        assertThat(stored).isPresent();
        assertThat(stored.get().getId()).isEqualTo(first.getId());
        assertThat(stored.get().getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(stored.get().getPayload()).contains("\"round\"");
        assertThat(stored.get().getAvailableAt()).isNotNull();
        assertThat(stored.get().getCreatedAt()).isNotNull();
    }

    @Test
    void claimMarksProcessingAndIncrementsAttempts() {
        repo.save(newEvent("k1"));
        repo.save(newEvent("k2"));

        List<OutboxEvent> claimed = repo.claimBatchForProcessing(10);

        assertThat(claimed).hasSize(2);
        assertThat(claimed).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.PROCESSING);
            assertThat(e.getAttempts()).isEqualTo(1);
        });

        // Already-claimed rows are not eligible again
        assertThat(repo.claimBatchForProcessing(10)).isEmpty();
    }

    @Test
    void claimRespectsBatchSizeAndAvailability() {
        repo.save(newEvent("due"));
        OutboxEvent future = newEvent("future");
        repo.save(future);
        dsl.update(T_OUTBOX_EVENT)
                .set(T_OUTBOX_EVENT.C_AVAILABLE_AT, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .where(T_OUTBOX_EVENT.C_IDEMPOTENCY_KEY.eq("future"))
                .execute();

        List<OutboxEvent> claimed = repo.claimBatchForProcessing(10);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getIdempotencyKey()).isEqualTo("due");
    }

    @Test
    void concurrentClaimersGetDisjointBatches() throws Exception {
        for (int i = 0; i < 4; i++) {
            repo.save(newEvent("k" + i));
        }

        try (Connection jdbc2 = TestDbConnections.open()) {
            jdbc.setAutoCommit(false);
            try {
                List<OutboxEvent> firstBatch = repo.claimBatchForProcessing(2); // locks held until commit

                DSLContext dsl2 = DSL.using(jdbc2, SQLDialect.POSTGRES);
                OutboxRepo repo2 = new OutboxPersistenceAdapter(dsl2);
                List<OutboxEvent> secondBatch = repo2.claimBatchForProcessing(10);

                assertThat(firstBatch).hasSize(2);
                assertThat(secondBatch).hasSize(2);
                assertThat(secondBatch)
                        .extracting(OutboxEvent::getId)
                        .doesNotContainAnyElementsOf(
                                firstBatch.stream().map(OutboxEvent::getId).toList());

                jdbc.commit();
            } finally {
                jdbc.setAutoCommit(true);
            }
        }

        assertThat(repo.claimBatchForProcessing(10)).isEmpty();
    }

    @Test
    void markSentIsTerminal() {
        repo.save(newEvent("k1"));
        OutboxEvent claimed = repo.claimBatchForProcessing(1).get(0);

        repo.markSent(claimed.getId());

        OutboxEvent stored = repo.findByIdempotencyKey("k1").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OutboxEvent.Status.SENT);
        assertThat(stored.getProcessedAt()).isNotNull();
        assertThat(repo.claimBatchForProcessing(10)).isEmpty();
    }

    @Test
    void markFailedSchedulesRetryPerAvailableAt() {
        repo.save(newEvent("k1"));
        OutboxEvent claimed = repo.claimBatchForProcessing(1).get(0);

        // Failure with a future retry time: not claimable yet
        repo.markFailed(claimed.getId(), "boom", Instant.now().plusSeconds(3600));
        OutboxEvent stored = repo.findByIdempotencyKey("k1").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(stored.getLastError()).isEqualTo("boom");
        assertThat(repo.claimBatchForProcessing(10)).isEmpty();

        // Once the retry time has passed it is claimed again, with attempts carried forward
        repo.markFailed(claimed.getId(), "boom", Instant.now().minusSeconds(1));
        List<OutboxEvent> reclaimed = repo.claimBatchForProcessing(10);
        assertThat(reclaimed).hasSize(1);
        assertThat(reclaimed.get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    void deadLetterIsNeverReclaimed() {
        repo.save(newEvent("k1"));
        OutboxEvent claimed = repo.claimBatchForProcessing(1).get(0);

        repo.markDeadLetter(claimed.getId(), "gave up");

        OutboxEvent stored = repo.findByIdempotencyKey("k1").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OutboxEvent.Status.DEAD_LETTER);
        assertThat(stored.getLastError()).isEqualTo("gave up");
        assertThat(stored.getProcessedAt()).isNotNull();
        assertThat(repo.claimBatchForProcessing(10)).isEmpty();
    }

    @Test
    void resetStuckProcessingReturnsRowsToRetryCycle() {
        repo.save(newEvent("k1"));
        repo.claimBatchForProcessing(1); // committed PROCESSING, claim time = now

        // Cutoff before the claim time: nothing is stuck yet
        assertThat(repo.resetStuckProcessing(Instant.now().minusSeconds(600))).isZero();

        // Cutoff after the claim time: the row counts as stuck and is reset
        assertThat(repo.resetStuckProcessing(Instant.now().plusSeconds(5))).isEqualTo(1);

        List<OutboxEvent> reclaimed = repo.claimBatchForProcessing(10);
        assertThat(reclaimed).hasSize(1);
        assertThat(reclaimed.get(0).getAttempts()).isEqualTo(2);
    }
}
