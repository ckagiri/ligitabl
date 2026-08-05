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
import org.springframework.dao.DuplicateKeyException;
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
import com.ligitabl.api.notification.outbox.RoundLockedPayload;
import com.ligitabl.api.notification.outbox.RoundResultsEmailEnqueuer;
import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.api.notification.outbox.SeasonCompletedPayload;
import com.ligitabl.api.notification.outbox.SeasonInPlayPayload;
import com.ligitabl.api.notification.outbox.SeasonWelcomeEmailEnqueuer;
import com.ligitabl.api.notification.outbox.SeasonWelcomeFanoutPayload;
import com.ligitabl.api.notification.outbox.SeasonWelcomePayload;
import com.ligitabl.api.notification.outbox.SegmentResultsEmailEnqueuer;
import com.ligitabl.api.notification.outbox.SegmentResultsPayload;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionCommand;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionError;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionResult;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.HitDistribution;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Season;
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
    SegmentResultsEmailEnqueuer segmentResultsEnqueuer;

    @Mock
    SeasonWelcomeEmailEnqueuer seasonWelcomeEnqueuer;

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
                segmentResultsEnqueuer,
                seasonWelcomeEnqueuer,
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
        when(renderer.render(eq(EmailCommand.EmailType.SEASON_WELCOME), any()))
                .thenReturn(Either.right(new EmailContent("welcome subject", "<html/>", "text")));
        when(renderer.render(eq(EmailCommand.EmailType.SEGMENT_RESULTS), any()))
                .thenReturn(Either.right(new EmailContent("segment subject", "<html/>", "text")));
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

        verify(segmentResultsEnqueuer).enqueueForRound(payload);
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void segmentFanOutFailureRetriesTheWholeEvent() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("board query down"))
                .when(segmentResultsEnqueuer)
                .enqueueForRound(any());
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

        verify(segmentResultsEnqueuer).enqueueForSeasonCompleted(payload);
        verify(segmentResultsEnqueuer, never()).enqueueForRound(any());
        verify(outboxRepo).markSent(event.getId());
    }

    private String segmentResultsJson(SegmentResultsPayload.SegmentPlacement... placements) throws Exception {
        return objectMapper.writeValueAsString(new SegmentResultsPayload(
                userId, "alice@x.com", "Alice", "aaaaaaaabc", "2025-26", "r9", 9, List.of(placements)));
    }

    private static SegmentResultsPayload.SegmentPlacement sprint(int rank) {
        return new SegmentResultsPayload.SegmentPlacement("SPRINT", "S2", "Sprint 2", 5, 9, rank, 41, 210);
    }

    private static SegmentResultsPayload.SegmentPlacement quarter(int rank) {
        return new SegmentResultsPayload.SegmentPlacement("QUARTER", "Q1", "Quarter 1", 1, 9, rank, 58, 395);
    }

    private static SegmentResultsPayload.SegmentPlacement season(int rank) {
        return new SegmentResultsPayload.SegmentPlacement("FULL_SEASON", "FS", "Season", 1, 38, rank, 61, 1500);
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

        verify(emailProvider).sendSingle(eq("alice@x.com"), eq("segment subject"), eq("<html/>"), any());
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
        when(emailProvider.sendSingle(anyString(), anyString(), anyString(), any()))
                .thenReturn(Either.left(new EmailError.EmailProviderError("mailgun 500")));
        OutboxEvent event = claimedEvent(OutboxEventTypes.SEGMENT_RESULTS, segmentResultsJson(sprint(1)), 1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("Email send failed"), any());
        verify(outboxRepo, never()).markSent(any());
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
                .sendSingle(eq("bob@x.com"), eq("join subject"), eq("<html/>"), eq(EmailCommand.Priority.NORMAL));
        verify(outboxRepo).markSent(event.getId());
    }

    private Season activeSeason(UUID preSeasonOpensAtOffsetSeasonId) {
        return Season.builder()
                .id(preSeasonOpensAtOffsetSeasonId)
                .mainContestId(UUID.randomUUID())
                .preSeasonOpensAt(NOW.minusSeconds(86400).atOffset(ZoneOffset.UTC))
                .build();
    }

    @Test
    void roundLockedAutoJoinsEligibleUsersThenMarksSent() throws Exception {
        Season season = activeSeason(seasonId);
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        var ctx = new CreatePredictionUseCase.JoinCtx(
                season, Contest.builder().id(UUID.randomUUID()).build(), 1, 1);

        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(java.util.List.of(user1, user2));
        when(createPredictionUseCase.resolveJoinContext(season)).thenReturn(Either.right(ctx));
        when(createPredictionUseCase.executeWithContext(any(), eq(ctx), any()))
                .thenReturn(Either.right(new CreatePredictionResult(UUID.randomUUID(), UUID.randomUUID(), 1, "ok")));

        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_LOCKED,
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, UUID.randomUUID(), 1)),
                1);

        processor.processOne(event);

        verify(createPredictionUseCase).executeWithContext(eq(user1), eq(ctx), any(CreatePredictionCommand.class));
        verify(createPredictionUseCase).executeWithContext(eq(user2), eq(ctx), any(CreatePredictionCommand.class));
        verify(outboxRepo).markSent(event.getId());
        verify(outboxRepo, never()).markFailed(any(), any(), any());
    }

    @Test
    void roundLockedWithNoUnjoinedUsers_skipsContextResolutionButStillMarksSent() throws Exception {
        Season season = activeSeason(seasonId);
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(java.util.List.of());

        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_LOCKED,
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, UUID.randomUUID(), 1)),
                1);

        processor.processOne(event);

        verify(createPredictionUseCase, never()).resolveJoinContext(any());
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void roundLockedOneUserFailure_doesNotBlockOthersOrFailTheEvent() throws Exception {
        Season season = activeSeason(seasonId);
        UUID badUser = UUID.randomUUID();
        UUID goodUser = UUID.randomUUID();
        var ctx = new CreatePredictionUseCase.JoinCtx(
                season, Contest.builder().id(UUID.randomUUID()).build(), 1, 1);

        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(java.util.List.of(badUser, goodUser));
        when(createPredictionUseCase.resolveJoinContext(season)).thenReturn(Either.right(ctx));
        when(createPredictionUseCase.executeWithContext(eq(badUser), eq(ctx), any()))
                .thenThrow(new RuntimeException("boom"));
        when(createPredictionUseCase.executeWithContext(eq(goodUser), eq(ctx), any()))
                .thenReturn(Either.right(new CreatePredictionResult(UUID.randomUUID(), UUID.randomUUID(), 1, "ok")));

        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_LOCKED,
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, UUID.randomUUID(), 1)),
                1);

        processor.processOne(event);

        verify(createPredictionUseCase).executeWithContext(eq(goodUser), eq(ctx), any());
        verify(outboxRepo).markSent(event.getId());
        verify(outboxRepo, never()).markFailed(any(), any(), any());
    }

    @Test
    void roundLockedDatabaseFailure_abortsLoopImmediatelyInsteadOfBlamingTheRemainingUsers() throws Exception {
        // A DataAccessException aborts the shared transaction, so every later user would fail
        // too and be logged as if at fault. The loop must stop at the real culprit and let the
        // whole batch retry.
        Season season = activeSeason(seasonId);
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        var ctx = new CreatePredictionUseCase.JoinCtx(
                season, Contest.builder().id(UUID.randomUUID()).build(), 1, 1);

        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any()))
                .thenReturn(java.util.List.of(firstUser, laterUser));
        when(createPredictionUseCase.resolveJoinContext(season)).thenReturn(Either.right(ctx));
        // jOOQ's DataAccessException, not Spring's — that is what the repos actually throw
        // here, as OutboxFailureIsolationIT demonstrates against a real Postgres.
        when(createPredictionUseCase.executeWithContext(eq(firstUser), eq(ctx), any()))
                .thenThrow(new org.jooq.exception.DataAccessException("current transaction is aborted"));

        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_LOCKED,
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, UUID.randomUUID(), 1)),
                1);

        processor.processOne(event);

        verify(createPredictionUseCase, never()).executeWithContext(eq(laterUser), any(), any());
        verify(outboxRepo).markFailed(eq(event.getId()), contains("transaction is aborted"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void roundLockedSpringDatabaseFailure_alsoAbortsTheLoop() throws Exception {
        // The two DataAccessException types share no supertype, so both arms are exercised.
        Season season = activeSeason(seasonId);
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        var ctx = new CreatePredictionUseCase.JoinCtx(
                season, Contest.builder().id(UUID.randomUUID()).build(), 1, 1);

        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any()))
                .thenReturn(java.util.List.of(firstUser, laterUser));
        when(createPredictionUseCase.resolveJoinContext(season)).thenReturn(Either.right(ctx));
        when(createPredictionUseCase.executeWithContext(eq(firstUser), eq(ctx), any()))
                .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint"));

        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_LOCKED,
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, UUID.randomUUID(), 1)),
                1);

        processor.processOne(event);

        verify(createPredictionUseCase, never()).executeWithContext(eq(laterUser), any(), any());
        verify(outboxRepo).markFailed(eq(event.getId()), contains("duplicate key"), any());
    }

    @Test
    void roundLockedContextResolutionFails_skipsBatchButStillMarksSent() throws Exception {
        Season season = activeSeason(seasonId);
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsAfter(eq(seasonId), any())).thenReturn(java.util.List.of(UUID.randomUUID()));
        when(createPredictionUseCase.resolveJoinContext(season))
                .thenReturn(Either.left(new CreatePredictionError.CurrentRoundNotFound(seasonId)));

        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_LOCKED,
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, UUID.randomUUID(), 1)),
                1);

        processor.processOne(event);

        verify(createPredictionUseCase, never()).executeWithContext(any(), any(), any());
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void roundLockedSeasonNotFound_marksFailed() throws Exception {
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.empty());

        OutboxEvent event = claimedEvent(
                OutboxEventTypes.ROUND_LOCKED,
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, UUID.randomUUID(), 1)),
                1);

        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("Season not found"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    // --- SEASON_IN_PLAY ----------------------------------------------------------

    private Season inPlaySeason() {
        return Season.builder()
                .id(seasonId)
                .mainContestId(UUID.randomUUID())
                .preSeasonOpensAt(NOW.minusSeconds(30L * 86400).atOffset(ZoneOffset.UTC))
                .build();
    }

    private OutboxEvent seasonInPlayEvent() throws Exception {
        return claimedEvent(
                OutboxEventTypes.SEASON_IN_PLAY,
                objectMapper.writeValueAsString(new SeasonInPlayPayload(seasonId)),
                1);
    }

    private java.util.List<OutboxEvent> savedEvents() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void seasonInPlayAutoRegistersEligibleUsersAsRoundZeroThenMarksSent() throws Exception {
        Season season = inPlaySeason();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();

        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(java.util.List.of(user1, user2));
        when(createPredictionUseCase.resolveMainContest(season)).thenReturn(Either.right(mainContest));
        when(createPredictionUseCase.autoRegisterDefaultTable(any(), eq(season), eq(mainContest)))
                .thenReturn(Either.right(new CreatePredictionResult(UUID.randomUUID(), UUID.randomUUID(), 0, "ok")));

        OutboxEvent event = seasonInPlayEvent();
        processor.processOne(event);

        verify(createPredictionUseCase).autoRegisterDefaultTable(eq(user1), eq(season), eq(mainContest));
        verify(createPredictionUseCase).autoRegisterDefaultTable(eq(user2), eq(season), eq(mainContest));
        // Never the mid-season shape — that would cost these users their swap allowance.
        verify(createPredictionUseCase, never()).executeWithContext(any(), any(), any());
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonInPlayUsesTheSeasonsPreSeasonOpensAtAsTheAnchor() throws Exception {
        Season season = inPlaySeason();
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(java.util.List.of());

        processor.processOne(seasonInPlayEvent());

        ArgumentCaptor<java.time.OffsetDateTime> anchor = ArgumentCaptor.forClass(java.time.OffsetDateTime.class);
        verify(userRepo).findUnjoinedUserIdsActiveSince(eq(seasonId), anchor.capture());
        Assertions.assertThat(anchor.getValue()).isEqualTo(season.getPreSeasonOpensAt());
    }

    @Test
    void seasonInPlayWritesTheWelcomeFanoutEvenWhenNobodyNeededAutoJoining() throws Exception {
        // The trap: processRoundLocked returns early on an empty candidate list. Doing that here
        // would silently skip welcoming genuine pre-season registrants, who exist regardless of
        // whether anyone needed auto-joining.
        Season season = inPlaySeason();
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(java.util.List.of());

        OutboxEvent event = seasonInPlayEvent();
        processor.processOne(event);

        verify(createPredictionUseCase, never()).resolveMainContest(any());
        Assertions.assertThat(savedEvents())
                .singleElement()
                .satisfies(e -> {
                    Assertions.assertThat(e.getIdempotencyKey()).isEqualTo("season-welcome-fanout:" + seasonId);
                    Assertions.assertThat(e.getEventType()).isEqualTo(OutboxEventTypes.SEASON_WELCOME_FANOUT);
                });
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonInPlayWritesExactlyOneFanoutEventNotOnePerUser() throws Exception {
        Season season = inPlaySeason();
        Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any()))
                .thenReturn(java.util.List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        when(createPredictionUseCase.resolveMainContest(season)).thenReturn(Either.right(mainContest));
        when(createPredictionUseCase.autoRegisterDefaultTable(any(), any(), any()))
                .thenReturn(Either.right(new CreatePredictionResult(UUID.randomUUID(), UUID.randomUUID(), 0, "ok")));

        processor.processOne(seasonInPlayEvent());

        Assertions.assertThat(savedEvents()).hasSize(1);
    }

    @Test
    void seasonInPlayOneUserFailure_doesNotBlockOthersOrFailTheEvent() throws Exception {
        Season season = inPlaySeason();
        UUID badUser = UUID.randomUUID();
        UUID goodUser = UUID.randomUUID();
        Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();

        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any()))
                .thenReturn(java.util.List.of(badUser, goodUser));
        when(createPredictionUseCase.resolveMainContest(season)).thenReturn(Either.right(mainContest));
        when(createPredictionUseCase.autoRegisterDefaultTable(eq(badUser), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        when(createPredictionUseCase.autoRegisterDefaultTable(eq(goodUser), any(), any()))
                .thenReturn(Either.right(new CreatePredictionResult(UUID.randomUUID(), UUID.randomUUID(), 0, "ok")));

        OutboxEvent event = seasonInPlayEvent();
        processor.processOne(event);

        verify(createPredictionUseCase).autoRegisterDefaultTable(eq(goodUser), any(), any());
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonInPlayDatabaseFailure_abortsLoopAndFailsTheEvent() throws Exception {
        Season season = inPlaySeason();
        UUID firstUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        Contest mainContest = Contest.builder().id(UUID.randomUUID()).build();

        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any()))
                .thenReturn(java.util.List.of(firstUser, laterUser));
        when(createPredictionUseCase.resolveMainContest(season)).thenReturn(Either.right(mainContest));
        when(createPredictionUseCase.autoRegisterDefaultTable(eq(firstUser), any(), any()))
                .thenThrow(new org.jooq.exception.DataAccessException("current transaction is aborted"));

        OutboxEvent event = seasonInPlayEvent();
        processor.processOne(event);

        verify(createPredictionUseCase, never()).autoRegisterDefaultTable(eq(laterUser), any(), any());
        verify(outboxRepo).markFailed(eq(event.getId()), contains("transaction is aborted"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    @Test
    void seasonInPlayContestResolutionFails_skipsBatchButStillWelcomesAndMarksSent() throws Exception {
        Season season = inPlaySeason();
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));
        when(userRepo.findUnjoinedUserIdsActiveSince(eq(seasonId), any())).thenReturn(java.util.List.of(UUID.randomUUID()));
        when(createPredictionUseCase.resolveMainContest(season))
                .thenReturn(Either.left(new CreatePredictionError.MainContestNotFound()));

        OutboxEvent event = seasonInPlayEvent();
        processor.processOne(event);

        verify(createPredictionUseCase, never()).autoRegisterDefaultTable(any(), any(), any());
        Assertions.assertThat(savedEvents()).hasSize(1);
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonInPlayWithoutPreSeasonOpensAt_skipsAutoJoinButStillWelcomes() throws Exception {
        // Only reachable if the season was edited between enqueue and process — the enqueuer
        // guards it. Guessing a cohort would be worse than skipping one.
        Season season = Season.builder()
                .id(seasonId)
                .mainContestId(UUID.randomUUID())
                .preSeasonOpensAt(null)
                .build();
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.of(season));

        OutboxEvent event = seasonInPlayEvent();
        processor.processOne(event);

        verify(userRepo, never()).findUnjoinedUserIdsActiveSince(any(), any());
        Assertions.assertThat(savedEvents()).hasSize(1);
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonInPlaySeasonNotFound_marksFailed() throws Exception {
        when(seasonRepo.findById(seasonId)).thenReturn(java.util.Optional.empty());

        OutboxEvent event = seasonInPlayEvent();
        processor.processOne(event);

        verify(outboxRepo).markFailed(eq(event.getId()), contains("Season not found"), any());
        verify(outboxRepo, never()).markSent(any());
    }

    // --- SEASON_WELCOME_FANOUT / SEASON_WELCOME (task 80) ----------------------------------

    @Test
    void seasonWelcomeFanoutDelegatesThenMarksSent() throws Exception {
        SeasonWelcomeFanoutPayload payload = new SeasonWelcomeFanoutPayload(seasonId);
        OutboxEvent event = claimedEvent(
                OutboxEventTypes.SEASON_WELCOME_FANOUT, objectMapper.writeValueAsString(payload), 1);

        processor.processOne(event);

        verify(seasonWelcomeEnqueuer).enqueue(payload);
        verify(outboxRepo).markSent(event.getId());
    }

    @Test
    void seasonWelcomeFanoutFailure_marksFailedSoTheWholeExpansionRetries() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(seasonWelcomeEnqueuer)
                .enqueue(any());
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
                .sendSingle(eq("carol@x.com"), eq("welcome subject"), eq("<html/>"), eq(EmailCommand.Priority.NORMAL));
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

        verify(emailProvider, never()).sendSingle(any(), any(), any(), any());
        verify(outboxRepo).markFailed(eq(event.getId()), contains("Template render failed"), any());
    }

    @Test
    void seasonWelcomeSendFailure_marksFailed() throws Exception {
        when(emailProvider.sendSingle(anyString(), anyString(), anyString(), any()))
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
