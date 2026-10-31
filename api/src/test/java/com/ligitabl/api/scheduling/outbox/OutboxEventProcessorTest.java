package com.ligitabl.api.scheduling.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
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
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.notification.roundresults.EnqueueRoundResultsEmailsUseCase;
import com.ligitabl.api.usecase.notification.seasonwelcome.EnqueueSeasonWelcomeEmailsUseCase;
import com.ligitabl.api.usecase.notification.segmentresults.EnqueueSegmentResultsEmailsUseCase;
import com.ligitabl.api.usecase.prediction.autojoin.AutoJoinUseCase;
import com.ligitabl.api.usecase.shared.email.EmailCommand;
import com.ligitabl.api.usecase.shared.email.EmailContent;
import com.ligitabl.api.usecase.shared.email.EmailError;
import com.ligitabl.api.usecase.shared.email.EmailProvider;
import com.ligitabl.api.usecase.shared.email.EmailTemplateRenderer;
import com.ligitabl.api.usecase.shared.outbox.JoinReminderPayload;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.RoundAdvancedPayload;
import com.ligitabl.api.usecase.shared.outbox.RoundLockedPayload;
import com.ligitabl.api.usecase.shared.outbox.RoundResultsPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonCompletedPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonInPlayPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonWelcomeFanoutPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonWelcomePayload;
import com.ligitabl.api.usecase.shared.outbox.SegmentResultsPayload;
import com.ligitabl.model.domain.prediction.HitDistribution;
import com.ligitabl.model.outbox.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxEventProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    @Mock
    OutboxRepo outboxRepo;

    @Mock
    EnqueueRoundResultsEmailsUseCase enqueuer;

    @Mock
    EnqueueSegmentResultsEmailsUseCase segmentResultsEnqueuer;

    @Mock
    EnqueueSeasonWelcomeEmailsUseCase seasonWelcomeEnqueuer;

    @Mock
    EmailTemplateRenderer renderer;

    @Mock
    EmailProvider emailProvider;

    @Mock
    AutoJoinUseCase autoJoinUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxEventProcessor processor;

    private final UUID seasonId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        processor = new OutboxEventProcessor(
                outboxRepo,
                enqueuer,
                segmentResultsEnqueuer,
                seasonWelcomeEnqueuer,
                renderer,
                emailProvider,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                autoJoinUseCase);
        ReflectionTestUtils.setField(processor, "frontendUrl", "http://localhost:8080");

        when(renderer.render(eq(EmailCommand.EmailType.ROUND_RESULTS), any()))
                .thenReturn(Either.right(new EmailContent("subject", "<html/>", "text")));
        when(renderer.render(eq(EmailCommand.EmailType.JOIN_REMINDER), any()))
                .thenReturn(Either.right(new EmailContent("join subject", "<html/>", "text")));
        when(renderer.render(eq(EmailCommand.EmailType.SEASON_WELCOME), any()))
                .thenReturn(Either.right(new EmailContent("welcome subject", "<html/>", "text")));
        when(renderer.render(eq(EmailCommand.EmailType.SEGMENT_RESULTS), any()))
                .thenReturn(Either.right(new EmailContent("segment subject", "<html/>", "text")));
        when(emailProvider.sendSingle(anyString(), any(), any())).thenReturn(Either.right(null));
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
        RoundResultsPayload payload = RoundResultsPayload.builder()
                .userId(userId)
                .userEmail("alice@x.com")
                .userDisplayName("Alice")
                .userPublicId("aaaaaaaabc")
                .seasonSlug("s1")
                .round(round)
                .score(175)
                .currentRound(currentRound)
                .lastRound(lastRound)
                .hitDistribution(new HitDistribution(1, 1, 1, 1))
                .sprint(RoundResultsPayload.SprintPlacement.builder()
                        .label("S8")
                        .fromRound(21)
                        .toRound(23)
                        .rank(2)
                        .totalParticipants(120)
                        .movement(1)
                        .sprintBest(175)
                        .isNewSprintBest(true)
                        .build())
                .season(RoundResultsPayload.SeasonPlacement.builder()
                        .label("Season")
                        .fromRound(1)
                        .toRound(38)
                        .rank(18)
                        .totalParticipants(140)
                        .movement(1)
                        .seasonBest(1800)
                        .isNewSeasonBest(false)
                        .build())
                .quarter(new RoundResultsPayload.Placement("Q3", 5, 130))
                .build();
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    void roundFinalizedFansOutThenMarksSent() throws Exception {
        RoundAdvancedPayload payload = new RoundAdvancedPayload(seasonId, 22, 22);
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_ADVANCED, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        verify(enqueuer).execute(payload);
        verify(outboxRepo).markSent(event.getId());
        verify(outboxRepo, never()).markFailed(any(), any(), any());
    }

    /**
     * ROUND_ADVANCED drives two fan-outs. The segment one no-ops on most rounds, but it must be
     * <em>called</em> on every one — deciding whether the round closes a segment is the enqueuer's
     * job, not the processor's.
     */
    @Test
    void roundAdvancedAlsoFansOutSegmentResults() throws Exception {
        RoundAdvancedPayload payload = new RoundAdvancedPayload(seasonId, 9, 10);
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_ADVANCED, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        verify(segmentResultsEnqueuer).execute(payload);
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void segmentFanOutFailureRetriesTheWholeEvent() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("board query down"))
                .when(segmentResultsEnqueuer)
                .execute(any(RoundAdvancedPayload.class));
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_ADVANCED,
                objectMapper.writeValueAsString(new RoundAdvancedPayload(seasonId, 9, 10)),
                1);

        processor.processOne(event);

        // Both expansions replay on retry; that is safe because every insert they make is keyed.
        verify(outboxRepo).markFailed(eq(event.getId()), contains("board query down"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void seasonCompletedFansOutTheFinalPodium() throws Exception {
        SeasonCompletedPayload payload = new SeasonCompletedPayload(seasonId);
        OutboxEvent event =
                claimedEvent(OutboxEventTypes.SEASON_COMPLETED, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        verify(segmentResultsEnqueuer).execute(payload);
        verify(segmentResultsEnqueuer, never()).execute(any(RoundAdvancedPayload.class));
        verify(outboxRepo).markSent(event.getId());
    }

    private String segmentResultsJson(SegmentResultsPayload.SegmentPlacement... placements) throws Exception {
        return objectMapper.writeValueAsString(SegmentResultsPayload.builder()
                .userId(userId)
                .userEmail("alice@x.com")
                .userDisplayName("Alice")
                .userPublicId("aaaaaaaabc")
                .seasonSlug("2025-26")
                .scopeKey("r9")
                .boundaryRound(9)
                .placements(List.of(placements))
                .build());
    }

    private static SegmentResultsPayload.SegmentPlacement sprint(int rank) {
        return SegmentResultsPayload.SegmentPlacement.builder()
                .type("SPRINT")
                .code("S2")
                .name("Sprint 2")
                .fromRound(5)
                .toRound(9)
                .rank(rank)
                .totalParticipants(41)
                .totalScore(210)
                .build();
    }

    private static SegmentResultsPayload.SegmentPlacement quarter(int rank) {
        return SegmentResultsPayload.SegmentPlacement.builder()
                .type("QUARTER")
                .code("Q1")
                .name("Quarter 1")
                .fromRound(1)
                .toRound(9)
                .rank(rank)
                .totalParticipants(58)
                .totalScore(395)
                .build();
    }

    private static SegmentResultsPayload.SegmentPlacement season(int rank) {
        return SegmentResultsPayload.SegmentPlacement.builder()
                .type("FULL_SEASON")
                .code("FS")
                .name("Season")
                .fromRound(1)
                .toRound(38)
                .rank(rank)
                .totalParticipants(61)
                .totalScore(1500)
                .build();
    }

    @Test
    void segmentResultsRendersAndSendsThenMarksSent() throws Exception {
        OutboxEvent event = claimedEvent(OutboxEventTypes.SEGMENT_RESULTS, segmentResultsJson(sprint(2)), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.SEGMENT_RESULTS), dataCaptor.capture());
        Assertions.assertThat(dataCaptor.getValue())
                .containsEntry("userDisplayName", "Alice")
                .containsEntry("headlineName", "Sprint 2")
                .containsEntry("headlineRank", 2)
                .containsEntry("isDouble", false)
                .containsEntry("isSeasonFinale", false)
                .containsEntry("leaderboardUrl", "http://localhost:8080/leaderboard?phase=S2");

        verify(emailProvider)
                .sendSingle(
                        eq("alice@x.com"),
                        argThat(c -> "segment subject".equals(c.subject()) && "<html/>".equals(c.htmlBody())),
                        any());
        verify(outboxRepo).markSent(event.getId());
    }

    /**
     * Placements arrive smallest-window-first, so the headline is the <em>last</em>. A user who
     * took both the sprint and the quarter it closes must be told about the quarter — the bigger
     * achievement — not whichever block sorts first.
     */
    @Test
    void segmentResultsHeadlinesTheLargestSegment() throws Exception {
        OutboxEvent event =
                claimedEvent(OutboxEventTypes.SEGMENT_RESULTS, segmentResultsJson(sprint(1), quarter(2)), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.SEGMENT_RESULTS), dataCaptor.capture());
        Assertions.assertThat(dataCaptor.getValue())
                .containsEntry("headlineName", "Quarter 1")
                .containsEntry("headlineRank", 2)
                .containsEntry("isDouble", true)
                .containsEntry("leaderboardUrl", "http://localhost:8080/leaderboard?phase=Q1");
    }

    @Test
    void segmentResultsFlagsTheSeasonFinale() throws Exception {
        OutboxEvent event = claimedEvent(OutboxEventTypes.SEGMENT_RESULTS, segmentResultsJson(season(1)), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.SEGMENT_RESULTS), dataCaptor.capture());
        Assertions.assertThat(dataCaptor.getValue())
                .containsEntry("isSeasonFinale", true)
                .containsEntry("isDouble", false);
    }

    @Test
    void segmentResultsRenderFailureRetries() throws Exception {
        when(renderer.render(eq(EmailCommand.EmailType.SEGMENT_RESULTS), any()))
                .thenReturn(Either.left(new EmailError.TemplateRenderError("SEGMENT_RESULTS", "boom")));
        OutboxEvent event = claimedEvent(OutboxEventTypes.SEGMENT_RESULTS, segmentResultsJson(sprint(1)), 1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("Template render failed"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void segmentResultsSendFailureRetries() throws Exception {
        when(emailProvider.sendSingle(anyString(), any(), any()))
                .thenReturn(Either.left(new EmailError.EmailProviderError("mailgun 500")));
        OutboxEvent event = claimedEvent(OutboxEventTypes.SEGMENT_RESULTS, segmentResultsJson(sprint(1)), 1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("Email send failed"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void rateLimitedSendDefersUntilProviderResetWithoutSpendingAnAttempt() throws Exception {
        Instant reset = NOW.plus(Duration.ofHours(9));
        when(emailProvider.sendSingle(anyString(), any(), any()))
                .thenReturn(Either.left(new EmailError.RateLimited("recipient limit (26) exceeded", reset)));
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 1);

        processor.processOne(event);

        verify(outboxRepo).markDeferred(eq(event.getId()), contains("rate limited"), eq(reset));
        verify(outboxRepo, never()).markFailed(any(), any(), any());
        verify(outboxRepo, never()).markDeadLetter(any(), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void rateLimitedSendWithoutResetTimeFallsBackToNormalBackoff() throws Exception {
        when(emailProvider.sendSingle(anyString(), any(), any()))
                .thenReturn(Either.left(new EmailError.RateLimited("limit exceeded", null)));
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 2);

        processor.processOne(event);

        // Attempt 2's backoff is 5 minutes.
        verify(outboxRepo)
                .markDeferred(eq(event.getId()), contains("rate limited"), eq(NOW.plus(Duration.ofMinutes(5))));
    }

    @Test
    void rateLimitedSendIsNeverDeadLetteredEvenWithAttemptsExhausted() throws Exception {
        Instant reset = NOW.plus(Duration.ofHours(9));
        when(emailProvider.sendSingle(anyString(), any(), any()))
                .thenReturn(Either.left(new EmailError.RateLimited("limit exceeded", reset)));
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 5);

        processor.processOne(event);

        verify(outboxRepo).markDeferred(eq(event.getId()), contains("rate limited"), eq(reset));
        verify(outboxRepo, never()).markDeadLetter(any(), any());
    }

    @Test
    void alreadyPastResetTimeStillWaitsTheBackoffRatherThanRetryingImmediately() throws Exception {
        when(emailProvider.sendSingle(anyString(), any(), any()))
                .thenReturn(Either.left(new EmailError.RateLimited("limit exceeded", NOW.minus(Duration.ofHours(1)))));
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 1);

        processor.processOne(event);

        verify(outboxRepo)
                .markDeferred(eq(event.getId()), contains("rate limited"), eq(NOW.plus(Duration.ofMinutes(1))));
    }

    @Test
    void roundResultsRendersAndSendsThenMarksSent() throws Exception {
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.ROUND_RESULTS), dataCaptor.capture());
        Assertions.assertThat(dataCaptor.getValue())
                .containsEntry("round", 22)
                .containsEntry("score", 175)
                .containsEntry("userDisplayName", "Alice")
                .containsEntry("showDetailedResultsLink", true)
                .containsEntry("detailedResultsUrl", "http://localhost:8080/u/aaaaaaaabc/s1/gw/22")
                .containsKeys("hitDistribution", "sprint", "quarter", "season");

        verify(emailProvider)
                .sendSingle(
                        eq("alice@x.com"),
                        argThat(c -> "subject".equals(c.subject()) && "<html/>".equals(c.htmlBody())),
                        eq(EmailCommand.Priority.NORMAL));
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
        Assertions.assertThat(dataCaptor.getValue()).containsEntry("showDetailedResultsLink", true);
    }

    @Test
    void hidesDetailedResultsLinkWhenTheReportedRoundIsTheSeasonFinale() throws Exception {
        // Round 38 (the last round) finalized — season is fully complete, no next round to link
        // to, so currentRound stays pinned at 38 too.
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_RESULTS, roundResultsJson(38, 38, 38), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.ROUND_RESULTS), dataCaptor.capture());
        Assertions.assertThat(dataCaptor.getValue()).containsEntry("showDetailedResultsLink", false);
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
        verify(emailProvider, never()).sendSingle(any(), any(), any());
    }

    @Test
    void sendFailureMarksFailedWithBackoff() throws Exception {
        when(emailProvider.sendSingle(anyString(), any(), any()))
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
        when(emailProvider.sendSingle(anyString(), any(), any()))
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
        JoinReminderPayload payload = new JoinReminderPayload(userId, "bob@x.com", 4);
        OutboxEvent event = claimedEvent(OutboxEventTypes.JOIN_REMINDER, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.JOIN_REMINDER), dataCaptor.capture());
        Assertions.assertThat(dataCaptor.getValue())
                .containsEntry("stage", 4)
                .containsEntry("myTableUrl", "http://localhost:8080/my-table")
                .containsEntry("leaderboardUrl", "http://localhost:8080/leaderboard");

        verify(emailProvider)
                .sendSingle(
                        eq("bob@x.com"),
                        argThat(c -> "join subject".equals(c.subject()) && "<html/>".equals(c.htmlBody())),
                        eq(EmailCommand.Priority.NORMAL));
        verify(outboxRepo).markSent(event.getId());
    }

    // --- ROUND_LOCKED / SEASON_IN_PLAY: auto-join rules live in AutoJoinUseCase ----

    @Test
    void roundLockedDelegatesToAutoJoinThenMarksSent() throws Exception {
        RoundLockedPayload payload = new RoundLockedPayload(seasonId, UUID.randomUUID(), 1);
        OutboxEvent event = claimedEvent(OutboxEventTypes.ROUND_LOCKED, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        verify(autoJoinUseCase).execute(payload);
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonInPlayDelegatesToAutoJoinThenMarksSent() throws Exception {
        SeasonInPlayPayload payload = new SeasonInPlayPayload(seasonId);
        OutboxEvent event = claimedEvent(OutboxEventTypes.SEASON_IN_PLAY, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        verify(autoJoinUseCase).execute(payload);
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void autoJoinDatabaseFailure_marksFailedNotSent() throws Exception {
        org.mockito.Mockito.doThrow(new org.jooq.exception.DataAccessException("current transaction is aborted"))
                .when(autoJoinUseCase)
                .execute(any(RoundLockedPayload.class));
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_LOCKED,
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, UUID.randomUUID(), 1)),
                1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("transaction is aborted"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    // --- SEASON_WELCOME_FANOUT / SEASON_WELCOME -----------------------------------

    @Test
    void seasonWelcomeFanoutDelegatesThenMarksSent() throws Exception {
        SeasonWelcomeFanoutPayload payload = new SeasonWelcomeFanoutPayload(seasonId);
        OutboxEvent event =
                claimedEvent(OutboxEventTypes.SEASON_WELCOME_FANOUT, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        verify(seasonWelcomeEnqueuer).execute(payload);
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonWelcomeFanoutFailure_marksFailedSoTheWholeExpansionRetries() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(seasonWelcomeEnqueuer)
                .execute(any());
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.SEASON_WELCOME_FANOUT,
                objectMapper.writeValueAsString(new SeasonWelcomeFanoutPayload(seasonId)),
                1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("db down"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void seasonWelcomeRendersAndSendsThenMarksSent() throws Exception {
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.SEASON_WELCOME,
                objectMapper.writeValueAsString(new SeasonWelcomePayload(userId, "carol@x.com")),
                1);

        processor.processOne(event);

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.captor();
        verify(renderer).render(eq(EmailCommand.EmailType.SEASON_WELCOME), dataCaptor.capture());
        Assertions.assertThat(dataCaptor.getValue())
                .containsEntry("myTableUrl", "http://localhost:8080/my-table")
                .containsEntry("leaderboardUrl", "http://localhost:8080/leaderboard")
                .containsEntry("faqUrl", "http://localhost:8080/faq")
                // The public, linkable address — /predictions/user/what-if is internal and is
                // reached in-app by an htmx swap that never changes the URL.
                .containsEntry("whatIfUrl", "http://localhost:8080/my-table/what-if");

        verify(emailProvider)
                .sendSingle(
                        eq("carol@x.com"),
                        argThat(c -> "welcome subject".equals(c.subject()) && "<html/>".equals(c.htmlBody())),
                        eq(EmailCommand.Priority.NORMAL));
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonWelcomeRenderFailure_marksFailed() throws Exception {
        when(renderer.render(eq(EmailCommand.EmailType.SEASON_WELCOME), any()))
                .thenReturn(Either.left(new EmailError.TemplateRenderError("email/season-welcome", "boom")));
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.SEASON_WELCOME,
                objectMapper.writeValueAsString(new SeasonWelcomePayload(userId, "carol@x.com")),
                1);

        processor.processOne(event);

        verify(emailProvider, never()).sendSingle(any(), any(), any());
        verify(outboxRepo).markFailed(eq(event.getId()), contains("Template render failed"), any());
    }

    @Test
    void seasonWelcomeSendFailure_marksFailed() throws Exception {
        when(emailProvider.sendSingle(anyString(), any(), any()))
                .thenReturn(Either.left(new EmailError.EmailProviderError("mailgun 500")));
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.SEASON_WELCOME,
                objectMapper.writeValueAsString(new SeasonWelcomePayload(userId, "carol@x.com")),
                1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("Email send failed"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void fanOutFailureMarksFailedNotSent() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(enqueuer)
                .execute(any());
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_ADVANCED,
                objectMapper.writeValueAsString(new RoundAdvancedPayload(seasonId, 22, 22)),
                1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("db down"), eq(NOW.plus(Duration.ofMinutes(1))));
        verify(outboxRepo, never()).markSent(any());
    }
}
