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
import com.ligitabl.api.notification.outbox.JoinReminderPayload;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundAdvancedPayload;
import com.ligitabl.api.notification.outbox.RoundResultsEmailEnqueuer;
import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.HitDistribution;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

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

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    UserRepo userRepo;

    @Mock
    CreatePredictionUseCase createPredictionUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxEventProcessor processor;

    private final UUID seasonId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        processor = new OutboxEventProcessor(
                outboxRepo,
                enqueuer,
                renderer,
                emailProvider,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                seasonRepo,
                userRepo,
                createPredictionUseCase);
        ReflectionTestUtils.setField(processor, "frontendUrl", "http://localhost:8080");

        when(renderer.render(eq(EmailCommand.EmailType.ROUND_RESULTS), any()))
                .thenReturn(Either.right(new EmailContent("subject", "<html/>", "text")));
        when(renderer.render(eq(EmailCommand.EmailType.JOIN_REMINDER), any()))
                .thenReturn(Either.right(new EmailContent("join subject", "<html/>", "text")));
        when(emailProvider.sendSingle(anyString(), anyString(), anyString(), any()))
                .thenReturn(Either.right(null));
    }

    private OutboxEvent claimedEvent(String type, String payload, int attempts) {
        return OutboxEvent.create("key-" + type, type, "round", "22", payload).toBuilder()
                .attempts(attempts)
                .build();
    }

    private String roundResultsJson() throws Exception {
        return roundResultsJson(22, 22, 38);
    }

    private String roundResultsJson(int round, int currentRound, int lastRound) throws Exception {
        RoundResultsPayload payload = new RoundResultsPayload(
                userId,
                "alice@x.com",
                "Alice",
                "aaaaaaaabc",
                "s1",
                round,
                175,
                currentRound,
                lastRound,
                new HitDistribution(1, 1, 1, 1),
                new RoundResultsPayload.SprintPlacement("S8", 21, 23, 2, 120, 1, 175, true),
                new RoundResultsPayload.SeasonPlacement("Season", 1, 38, 18, 140, 1, 1800, false),
                new RoundResultsPayload.Placement("Q3", 5, 130));
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    void roundFinalizedFansOutThenMarksSent() throws Exception {
        RoundAdvancedPayload payload = new RoundAdvancedPayload(seasonId, 22, 22);
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_ADVANCED, objectMapper.writeValueAsString(payload), 1);

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
                .containsEntry("detailedResultsUrl", "http://localhost:8080/u/aaaaaaaabc/s1/gw/22")
                .containsKeys("hitDistribution", "sprint", "quarter", "season");

        verify(emailProvider)
                .sendSingle(eq("alice@x.com"), eq("subject"), eq("<html/>"), eq(EmailCommand.Priority.NORMAL));
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void showsDetailedResultsLinkForSecondToLastRoundEvenThoughSeasonNowSitsOnTheLastRound() throws Exception {
        // Round 37 (of 38) just finalized, so the season's currentRound has already advanced to
        // 38 (the last round) — but this email is about round 37, which isn't the season finale,
        // so the CTA must still show.
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(37, 38, 38), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.ROUND_RESULTS), dataCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(dataCaptor.getValue()).containsEntry("showDetailedResultsLink", true);
    }

    @Test
    void hidesDetailedResultsLinkWhenTheReportedRoundIsTheSeasonFinale() throws Exception {
        // Round 38 (the last round) finalized — season is fully complete, no next round to link
        // to, so currentRound stays pinned at 38 too.
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(38, 38, 38), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.ROUND_RESULTS), dataCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(dataCaptor.getValue()).containsEntry("showDetailedResultsLink", false);
    }

    @Test
    void renderFailureMarksFailedWithBackoff() throws Exception {
        when(renderer.render(any(), any()))
                .thenReturn(Either.left(new EmailError.TemplateRenderError("email/round-results", "boom")));
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 1);

        processor.processOne(event);

        // attempts=1 → next retry in 1 minute
        verify(outboxRepo)
                .markFailed(eq(event.getId()), contains("Template render failed"), eq(NOW.plus(Duration.ofMinutes(1))));
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
        verify(outboxRepo)
                .markFailed(eq(event.getId()), contains("Email send failed"), eq(NOW.plus(Duration.ofMinutes(5))));
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
    void joinReminderRendersAndSendsThenMarksSent() throws Exception {
        JoinReminderPayload payload = new JoinReminderPayload(userId, "bob@x.com");
        OutboxEvent event = claimedEvent(OutboxEventTypes.JOIN_REMINDER, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.JOIN_REMINDER), dataCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(dataCaptor.getValue())
                .containsEntry("myTableUrl", "http://localhost:8080/my-table")
                .containsEntry("leaderboardUrl", "http://localhost:8080/leaderboard");

        verify(emailProvider).sendSingle(eq("bob@x.com"), eq("join subject"), eq("<html/>"), eq(EmailCommand.Priority.NORMAL));
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void fanOutFailureMarksFailedNotSent() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(enqueuer)
                .enqueue(any());
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_ADVANCED,
                objectMapper.writeValueAsString(new RoundAdvancedPayload(seasonId, 22, 22)),
                1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("db down"), eq(NOW.plus(Duration.ofMinutes(1))));
        verify(outboxRepo, never()).markSent(any());
    }
}
