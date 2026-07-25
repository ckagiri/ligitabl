package com.ligitabl.api.scheduling.outbox;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.notification.email.EmailCommand;
import com.ligitabl.api.notification.email.EmailContent;
import com.ligitabl.api.notification.email.EmailProvider;
import com.ligitabl.api.notification.email.EmailTemplateRenderer;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundAdvancedPayload;
import com.ligitabl.api.notification.outbox.RoundResultsEmailEnqueuer;
import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Processes one claimed outbox event: dispatches on event type, then marks the
 * terminal state. Separate bean from {@link OutboxRelayJob} so the
 * {@code @Transactional} boundary is a real proxy call — each event's work and
 * its markSent commit (or roll back) together.
 *
 * <p>Failure contract: any exception during processing marks the event FAILED
 * with backoff, or DEAD_LETTER once attempts are exhausted (the claim already
 * counted the in-flight attempt). Unknown event types dead-letter immediately —
 * retrying can't fix an event the code doesn't know how to handle. If the
 * failure was a database error that aborted the transaction, the markFailed
 * write itself may fail too; the row then stays PROCESSING until the stuck-row
 * sweep returns it to the retry cycle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private final OutboxRepo outboxRepo;
    private final RoundResultsEmailEnqueuer roundResultsEmailEnqueuer;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final EmailProvider emailProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${ligitabl.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Transactional
    public void processOne(OutboxEvent event) {
        try {
            switch (event.getEventType()) {
                case OutboxEventTypes.ROUND_ADVANCED -> processRoundAdvanced(event);
                case OutboxEventTypes.ROUND_RESULTS -> processRoundResults(event);
                default -> {
                    log.warn(
                            "[OUTBOX_DEAD_LETTER] id={}, type={}: unknown event type",
                            event.getId(),
                            event.getEventType());
                    outboxRepo.markDeadLetter(event.getId(), "Unknown event type: " + event.getEventType());
                    return;
                }
            }
            outboxRepo.markSent(event.getId());
            log.info("[OUTBOX_SENT] id={}, type={}", event.getId(), event.getEventType());
        } catch (Exception e) {
            handleFailure(event, e);
        }
    }

    /**
     * Fan-out: expands the thin round-advanced fact into per-user
     * ROUND_RESULTS events. Runs in this event's transaction together with the
     * parent's markSent, so a crash mid-fan-out retries the whole expansion —
     * duplicate-free via the per-user idempotency keys.
     */
    private void processRoundAdvanced(OutboxEvent event) throws Exception {
        RoundAdvancedPayload payload = objectMapper.readValue(event.getPayload(), RoundAdvancedPayload.class);
        roundResultsEmailEnqueuer.enqueue(payload);
    }

    private void processRoundResults(OutboxEvent event) throws Exception {
        RoundResultsPayload payload = objectMapper.readValue(event.getPayload(), RoundResultsPayload.class);

        EmailContent content = emailTemplateRenderer
                .render(EmailCommand.EmailType.ROUND_RESULTS, templateData(payload))
                .fold(
                        error -> {
                            throw new IllegalStateException("Template render failed: " + error);
                        },
                        c -> c);

        emailProvider
                .sendSingle(payload.userEmail(), content.subject(), content.htmlBody(), EmailCommand.Priority.NORMAL)
                .peekLeft(error -> {
                    throw new IllegalStateException("Email send failed: " + error);
                });
    }

    private Map<String, Object> templateData(RoundResultsPayload payload) {
        Map<String, Object> data = new HashMap<>();
        data.put("userDisplayName", payload.userDisplayName());
        data.put("round", payload.round());
        data.put("score", payload.score());
        data.put("currentRound", payload.currentRound());
        data.put("lastRound", payload.lastRound());
        data.put("hitDistribution", payload.hitDistribution());
        data.put("sprint", payload.sprint());
        data.put("quarter", payload.quarter());
        data.put("season", payload.season());
        data.put("frontendUrl", frontendUrl);
        data.put("showDetailedResultsLink", payload.currentRound() != payload.lastRound());
        data.put("detailedResultsUrl", frontendUrl + "/my-table?round=" + payload.round());
        return data;
    }

    private void handleFailure(OutboxEvent event, Exception e) {
        String error = e.getMessage() != null ? e.getMessage() : e.toString();
        if (event.hasExceededMaxAttempts()) {
            outboxRepo.markDeadLetter(event.getId(), error);
            log.error(
                    "[OUTBOX_DEAD_LETTER] id={}, type={}, attempts={}: {}",
                    event.getId(),
                    event.getEventType(),
                    event.getAttempts(),
                    error,
                    e);
        } else {
            var retryAt = event.nextAvailableAt(clock.instant());
            outboxRepo.markFailed(event.getId(), error, retryAt);
            log.warn(
                    "[OUTBOX_PROCESS_FAILED] id={}, type={}, attempt={}, retryAt={}: {}",
                    event.getId(),
                    event.getEventType(),
                    event.getAttempts(),
                    retryAt,
                    error);
        }
    }
}
