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

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;

/**
 * Drains the transactional outbox: claims batches of due PENDING/FAILED events
 * (FOR UPDATE SKIP LOCKED — multi-instance safe) and hands each to
 * {@link OutboxEventProcessor}. A second schedule sweeps PROCESSING rows
 * orphaned by a crash back into the retry cycle.
 *
 * <p>Each event is isolated: one that throws past the processor is logged,
 * marked failed out of band, and the batch continues. The batch is a unit of
 * claiming, not of failure.
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
            try {
                processor.processOne(event);
            } catch (Exception e) {
                // processOne swallows almost everything, but not a database error that
                // aborted its transaction — that also defeats its own markFailed write.
                // Without this guard the escaping exception abandons the rest of the
                // claimed batch, leaving up to batchSize-1 untouched events PROCESSING
                // until the stuck sweep.
                log.error("[OUTBOX_RELAY_EVENT_FAILED] id={}, type={}", event.getId(), event.getEventType(), e);
                Sentry.captureException(e);
                recordFailureOutOfBand(event, e);
            }
        }
    }

    /**
     * Retries the failure bookkeeping now that the exception has crossed the processor's
     * transactional proxy: Spring has rolled back, so this write runs on a clean
     * connection and commits, and the event re-enters the retry cycle with normal backoff
     * instead of waiting for {@link #recoverStuckProcessing}. Guarded in turn — a failure
     * to record a failure must not cost the remaining events their turn.
     */
    private void recordFailureOutOfBand(OutboxEvent event, Exception cause) {
        try {
            processor.recordFailure(event, cause);
        } catch (Exception e) {
            log.error("[OUTBOX_RELAY_MARK_FAILED_FAILED] id={}: {}", event.getId(), e.getMessage(), e);
            Sentry.captureException(e);
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
