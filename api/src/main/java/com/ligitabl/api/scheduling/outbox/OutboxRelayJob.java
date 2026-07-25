package com.ligitabl.api.scheduling.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

import lombok.extern.slf4j.Slf4j;

/**
 * Drains the transactional outbox: claims batches of due PENDING/FAILED events
 * (FOR UPDATE SKIP LOCKED — multi-instance safe) and hands each to
 * {@link OutboxEventProcessor}. A second schedule sweeps PROCESSING rows
 * orphaned by a crash back into the retry cycle.
 */
@Component
// Both flags must be true (or absent): ligitabl.scheduling.enabled is the global
// scheduler switch, ligitabl.outbox.enabled the outbox-specific kill-switch —
// flip OUTBOX_ENABLED=false to pause draining (events queue up as PENDING and
// are delivered once re-enabled; nothing is lost).
@ConditionalOnProperty(
        name = {"ligitabl.scheduling.enabled", "ligitabl.outbox.enabled"},
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class OutboxRelayJob {

    private static final Duration STUCK_PROCESSING_TIMEOUT = Duration.ofMinutes(10);

    private final OutboxRepo outboxRepo;
    private final OutboxEventProcessor processor;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelayJob(
            OutboxRepo outboxRepo,
            OutboxEventProcessor processor,
            Clock clock,
            @Value("${ligitabl.outbox.batch-size:25}") int batchSize) {
        this.outboxRepo = outboxRepo;
        this.processor = processor;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ligitabl.outbox.poll-interval-ms:15000}")
    public void relay() {
        List<OutboxEvent> batch = outboxRepo.claimBatchForProcessing(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        log.info("[OUTBOX_RELAY_BATCH] size={}", batch.size());
        for (OutboxEvent event : batch) {
            processor.processOne(event);
        }
    }

    /** Crash recovery: PROCESSING rows older than the timeout re-enter the retry cycle. */
    @Scheduled(
            fixedDelayString = "${ligitabl.outbox.stuck-sweep-interval-ms:300000}",
            initialDelayString = "${ligitabl.outbox.stuck-sweep-interval-ms:300000}")
    public void recoverStuckProcessing() {
        int reset = outboxRepo.resetStuckProcessing(clock.instant().minus(STUCK_PROCESSING_TIMEOUT));
        if (reset > 0) {
            log.warn("[OUTBOX_STUCK_RESET] count={}", reset);
        }
    }
}
