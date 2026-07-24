package com.ligitabl.api.scheduling.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.notification.email.EmailCommand;
import com.ligitabl.api.notification.email.EmailContent;
import com.ligitabl.api.notification.email.EmailError;
import com.ligitabl.api.notification.email.EmailProvider;
import com.ligitabl.api.notification.email.EmailTemplateRenderer;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundFinalizedPayload;
import com.ligitabl.api.notification.outbox.RoundResultsEmailEnqueuer;
import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.HitDistribution;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxEventProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @Mock
    OutboxRepo outboxRepo;

    @Mock
    RoundResultsEmailEnqueuer enqueuer;

    @Mock
    EmailTemplateRenderer renderer;

    @Mock
    EmailProvider emailProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxEventProcessor processor;

    private final UUID seasonId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        processor = new OutboxEventProcessor(
                outboxRepo, enqueuer, renderer, emailProvider, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(processor, "frontendUrl", "http://localhost:8080");

        when(renderer.render(eq(EmailCommand.EmailType.ROUND_RESULTS), any()))
                .thenReturn(Either.right(new EmailContent("subject", "<html/>", "text")));
        when(emailProvider.sendSingle(anyString(), anyString(), anyString(), any()))
                .thenReturn(Either.right(null));
    }

    private OutboxEvent claimedEvent(String type, String payload, int attempts) {
        return OutboxEvent.create("key-" + type, type, "round", "22", payload).toBuilder()
                .attempts(attempts)
                .build();
    }

    private String roundResultsJson() throws Exception {
        RoundResultsPayload payload = new RoundResultsPayload(
                userId,
                "alice@x.com",
                "Alice",
                22,
                175,
                22,
                38,
                new HitDistribution(1, 1, 1, 1),
                new RoundResultsPayload.SprintPlacement("S8", 21, 23, 2, 120, 1, 175, true),
                new RoundResultsPayload.Placement("Q3", 5, 130),
                new RoundResultsPayload.Placement("FS", 18, 140));
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    void roundFinalizedFansOutThenMarksSent() throws Exception {
        RoundFinalizedPayload payload = new RoundFinalizedPayload(seasonId, 22, 22);
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_FINALIZED, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        verify(enqueuer).enqueue(payload);
        verify(outboxRepo).markSent(event.getId());
        verify(outboxRepo, never()).markFailed(any(), any(), any());
    }

    @Test
    void roundResultsRendersAndSendsThenMarksSent() throws Exception {
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.ROUND_RESULTS), dataCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(dataCaptor.getValue())
                .containsEntry("round", 22)
                .containsEntry("score", 175)
                .containsEntry("userDisplayName", "Alice")
                .containsEntry("showDetailedResultsLink", true)
                .containsEntry("detailedResultsUrl", "http://localhost:8080/my-table?round=22")
                .containsKeys("hitDistribution", "sprint", "quarter", "season");

        verify(emailProvider)
                .sendSingle(eq("alice@x.com"), eq("subject"), eq("<html/>"), eq(EmailCommand.Priority.NORMAL));
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void renderFailureMarksFailedWithBackoff() throws Exception {
        when(renderer.render(any(), any()))
                .thenReturn(Either.left(new EmailError.TemplateRenderError("email/round-results", "boom")));
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 1);

        processor.processOne(event);

        // attempts=1 → next retry in 1 minute
        verify(outboxRepo).markFailed(eq(event.getId()), contains("Template render failed"), eq(NOW.plus(Duration.ofMinutes(1))));
        verify(outboxRepo, never()).markSent(any());
        verify(emailProvider, never()).sendSingle(any(), any(), any(), any());
    }

    @Test
    void sendFailureMarksFailedWithBackoff() throws Exception {
        when(emailProvider.sendSingle(anyString(), anyString(), anyString(), any()))
                .thenReturn(Either.left(new EmailError.EmailProviderError("mailgun 500")));
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 2);

        processor.processOne(event);

        // attempts=2 → next retry in 5 minutes
        verify(outboxRepo).markFailed(eq(event.getId()), contains("Email send failed"), eq(NOW.plus(Duration.ofMinutes(5))));
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void exhaustedAttemptsDeadLetter() throws Exception {
        when(emailProvider.sendSingle(anyString(), anyString(), anyString(), any()))
                .thenReturn(Either.left(new EmailError.EmailProviderError("mailgun 500")));
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 5);

        processor.processOne(event);

        verify(outboxRepo).markDeadLetter(eq(event.getId()), contains("Email send failed"));
        verify(outboxRepo, never()).markFailed(any(), any(), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void unknownEventTypeDeadLettersImmediately() {
        OutboxEvent event = claimedEvent("SOMETHING_ELSE", "{}", 1);

        processor.processOne(event);

        verify(outboxRepo).markDeadLetter(eq(event.getId()), contains("Unknown event type"));
        verify(outboxRepo, never()).markSent(any());
        verify(outboxRepo, never()).markFailed(any(), any(), any());
    }

    @Test
    void malformedPayloadMarksFailed() {
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, "not-json", 1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), anyString(), eq(NOW.plus(Duration.ofMinutes(1))));
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void fanOutFailureMarksFailedNotSent() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(enqueuer)
                .enqueue(any());
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_FINALIZED,
                objectMapper.writeValueAsString(new RoundFinalizedPayload(seasonId, 22, 22)),
                1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("db down"), eq(NOW.plus(Duration.ofMinutes(1))));
        verify(outboxRepo, never()).markSent(any());
    }
}
