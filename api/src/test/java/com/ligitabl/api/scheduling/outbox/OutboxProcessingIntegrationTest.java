package com.ligitabl.api.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.HitDistribution;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

/**
 * End-to-end outbox processing against the real database, template engine and
 * (logging) email provider: claim → dispatch → render → send → terminal state.
 * The scheduled relay itself is disabled in tests (ligitabl.scheduling.enabled
 * = false); this drives the same claim/process path the relay runs.
 */
@SpringBootTest
@DisplayName("Outbox processing integration")
class OutboxProcessingIntegrationTest extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxRepo outboxRepo;

    @Autowired
    OutboxEventProcessor processor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
    }

    private String roundResultsJson() throws Exception {
        RoundResultsPayload payload = new RoundResultsPayload(
                UUID.randomUUID(),
                "alice@example.com",
                "Alice",
                22,
                175,
                22,
                38,
                new HitDistribution(5, 8, 5, 2),
                new RoundResultsPayload.SprintPlacement("S8", 21, 23, 2, 120, 1, 175, true),
                new RoundResultsPayload.QuarterPlacement("Q3", 20, 28, 5, 130, 1, 175, true),
                new RoundResultsPayload.Placement("FS", 18, 140));
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    @DisplayName("ROUND_RESULTS event renders the template, sends, and is marked SENT")
    void processesRoundResultsToSent() throws Exception {
        outboxRepo.save(OutboxEvent.create(
                "round-results:it:22:alice", OutboxEventTypes.ROUND_RESULTS, "round", "22", roundResultsJson()));

        List<OutboxEvent> claimed = outboxRepo.claimBatchForProcessing(10);
        assertThat(claimed).hasSize(1);

        processor.processOne(claimed.get(0));

        OutboxEvent stored = outboxRepo.findByIdempotencyKey("round-results:it:22:alice").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OutboxEvent.Status.SENT);
        assertThat(stored.getProcessedAt()).isNotNull();
        assertThat(outboxRepo.claimBatchForProcessing(10)).isEmpty();
    }

    @Test
    @DisplayName("Undeserializable payload is marked FAILED with a future retry time")
    void malformedPayloadFails() {
        // The jsonb column enforces valid JSON, so "malformed" here means a type
        // mismatch that fails deserialization into RoundResultsPayload.
        outboxRepo.save(OutboxEvent.create(
                "round-results:it:22:bad",
                OutboxEventTypes.ROUND_RESULTS,
                "round",
                "22",
                "{\"round\":\"not-a-number\"}"));

        List<OutboxEvent> claimed = outboxRepo.claimBatchForProcessing(10);
        processor.processOne(claimed.get(0));

        OutboxEvent stored = outboxRepo.findByIdempotencyKey("round-results:it:22:bad").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(stored.getAttempts()).isEqualTo(1);
        assertThat(stored.getLastError()).isNotBlank();
        // Backoff pushed availability into the future — not immediately reclaimable
        assertThat(outboxRepo.claimBatchForProcessing(10)).isEmpty();
    }

    @Test
    @DisplayName("Unknown event type is dead-lettered immediately")
    void unknownTypeDeadLetters() {
        outboxRepo.save(OutboxEvent.create("mystery:1", "MYSTERY", "round", "1", "{}"));

        List<OutboxEvent> claimed = outboxRepo.claimBatchForProcessing(10);
        processor.processOne(claimed.get(0));

        OutboxEvent stored = outboxRepo.findByIdempotencyKey("mystery:1").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OutboxEvent.Status.DEAD_LETTER);
        assertThat(stored.getLastError()).contains("Unknown event type");
        assertThat(outboxRepo.claimBatchForProcessing(10)).isEmpty();
    }
}
