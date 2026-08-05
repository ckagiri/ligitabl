package com.ligitabl.api.scheduling.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

/**
 * Covers the batch-isolation contract: the claimed batch is a unit of claiming, not of
 * failure.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxRelayJobTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    @Mock
    OutboxRepo outboxRepo;

    @Mock
    OutboxEventProcessor processor;

    private OutboxRelayJob job;

    @BeforeEach
    void setup() {
        job = new OutboxRelayJob(outboxRepo, processor, Clock.fixed(NOW, ZoneOffset.UTC), 25);
    }

    private OutboxEvent event(String key) {
        return OutboxEvent.create(key, OutboxEventTypes.ROUND_RESULTS, "round", "22", "{}");
    }

    @Test
    void oneThrowingEventDoesNotStopTheRestOfTheBatch() {
        OutboxEvent first = event("k1");
        OutboxEvent poisoned = event("k2");
        OutboxEvent last = event("k3");
        when(outboxRepo.claimBatchForProcessing(25)).thenReturn(List.of(first, poisoned, last));

        doNothing().when(processor).processOne(first);
        doThrow(new DuplicateKeyException("aborted")).when(processor).processOne(poisoned);
        doNothing().when(processor).processOne(last);

        job.relay();

        verify(processor).processOne(first);
        verify(processor).processOne(last);
    }

    @Test
    void throwingEventIsMarkedFailedOutOfBandRatherThanLeftProcessing() {
        OutboxEvent poisoned = event("k1");
        when(outboxRepo.claimBatchForProcessing(25)).thenReturn(List.of(poisoned));

        DuplicateKeyException cause = new DuplicateKeyException("aborted");
        doThrow(cause).when(processor).processOne(poisoned);

        job.relay();

        // Recorded from outside the rolled-back transaction, so the event re-enters the
        // retry cycle now instead of waiting on recoverStuckProcessing.
        verify(processor).recordFailure(poisoned, cause);
    }

    @Test
    void failureToRecordAFailureStillLetsTheBatchContinue() {
        OutboxEvent poisoned = event("k1");
        OutboxEvent last = event("k2");
        when(outboxRepo.claimBatchForProcessing(25)).thenReturn(List.of(poisoned, last));

        doThrow(new DuplicateKeyException("aborted")).when(processor).processOne(poisoned);
        doThrow(new IllegalStateException("connection gone"))
                .when(processor)
                .recordFailure(eq(poisoned), any());
        doNothing().when(processor).processOne(last);

        job.relay();

        verify(processor).processOne(last);
    }

    @Test
    void healthyBatchNeverRecordsFailures() {
        OutboxEvent first = event("k1");
        OutboxEvent second = event("k2");
        when(outboxRepo.claimBatchForProcessing(25)).thenReturn(List.of(first, second));

        job.relay();

        verify(processor).processOne(first);
        verify(processor).processOne(second);
        verify(processor, never()).recordFailure(any(), any());
    }

    @Test
    void emptyBatchIsANoOp() {
        when(outboxRepo.claimBatchForProcessing(25)).thenReturn(List.of());

        job.relay();

        verify(processor, never()).processOne(any());
    }
}
