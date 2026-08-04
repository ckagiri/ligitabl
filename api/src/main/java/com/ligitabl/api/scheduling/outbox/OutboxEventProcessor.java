package com.ligitabl.api.scheduling.outbox;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.notification.email.EmailCommand;
import com.ligitabl.api.notification.email.EmailContent;
import com.ligitabl.api.notification.email.EmailProvider;
import com.ligitabl.api.notification.email.EmailTemplateRenderer;
import com.ligitabl.api.notification.outbox.JoinReminderPayload;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundAdvancedPayload;
import com.ligitabl.api.notification.outbox.RoundLockedPayload;
import com.ligitabl.api.notification.outbox.RoundResultsEmailEnqueuer;
import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.api.notification.outbox.SeasonInPlayPayload;
import com.ligitabl.api.notification.outbox.SeasonWelcomeEmailEnqueuer;
import com.ligitabl.api.notification.outbox.SeasonWelcomeFanoutPayload;
import com.ligitabl.api.notification.outbox.SeasonWelcomePayload;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionCommand;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

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
 * write itself fails too and the exception escapes this method;
 * {@link OutboxRelayJob} then retries {@link #recordFailure} from outside the
 * rolled-back transaction, so the row still enters the retry cycle promptly
 * rather than waiting on the stuck-row sweep.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private final OutboxRepo outboxRepo;
    private final RoundResultsEmailEnqueuer roundResultsEmailEnqueuer;
    private final SeasonWelcomeEmailEnqueuer seasonWelcomeEmailEnqueuer;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final EmailProvider emailProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SeasonRepo seasonRepo;
    private final UserRepo userRepo;
    private final CreatePredictionUseCase createPredictionUseCase;

    @Value("${ligitabl.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Transactional
    public void processOne(OutboxEvent event) {
        try {
            switch (event.getEventType()) {
                case OutboxEventTypes.ROUND_ADVANCED -> processRoundAdvanced(event);
                case OutboxEventTypes.ROUND_RESULTS -> processRoundResults(event);
                case OutboxEventTypes.ROUND_LOCKED -> processRoundLocked(event);
                case OutboxEventTypes.SEASON_IN_PLAY -> processSeasonInPlay(event);
                case OutboxEventTypes.SEASON_WELCOME_FANOUT -> processSeasonWelcomeFanout(event);
                case OutboxEventTypes.SEASON_WELCOME -> processSeasonWelcome(event);
                case OutboxEventTypes.JOIN_REMINDER -> processJoinReminder(event);
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
            recordFailure(event, e);
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

    /**
     * Auto-joins users who registered after the season's pre-season window opened and never
     * created a SeasonPrediction — evaluated fresh at processing time, not from the event's
     * payload, so it reflects whichever round/rankings are current when this actually runs.
     */
    private void processRoundLocked(OutboxEvent event) throws Exception {
        RoundLockedPayload payload = objectMapper.readValue(event.getPayload(), RoundLockedPayload.class);

        Season season = seasonRepo
                .findById(payload.seasonId())
                .orElseThrow(() -> new IllegalStateException("Season not found: " + payload.seasonId()));

        List<UUID> unjoinedUserIds = userRepo.findUnjoinedUserIdsAfter(season.getId(), season.getPreSeasonOpensAt());
        if (unjoinedUserIds.isEmpty()) {
            return;
        }

        createPredictionUseCase
                .resolveJoinContext(season)
                .fold(
                        error -> {
                            log.warn(
                                    "[ROUND_LOCKED] Skipping auto-join batch for season {}: {}", season.getId(), error);
                            return null;
                        },
                        ctx -> {
                            for (UUID userId : unjoinedUserIds) {
                                try {
                                    createPredictionUseCase
                                            .executeWithContext(userId, ctx, new CreatePredictionCommand(List.of()))
                                            .peekLeft(error -> log.warn(
                                                    "[ROUND_LOCKED] Auto-join skipped for user {}: {}", userId, error));
                                } catch (org.jooq.exception.DataAccessException
                                        | org.springframework.dao.DataAccessException e) {
                                    // Not survivable per-user: the batch shares one transaction, and a
                                    // failed statement aborts it, so every remaining user would fail too
                                    // and be logged as if they were at fault. Fail now, attributed
                                    // correctly — the whole batch retries, and the NOT EXISTS filter
                                    // skips anyone already created.
                                    //
                                    // Both types are caught deliberately: the repos surface jOOQ's
                                    // DataAccessException (verified in OutboxFailureIsolationIT), while
                                    // Spring's is what a translated JDBC path would throw. They share no
                                    // supertype, so neither alone is sufficient.
                                    throw e;
                                } catch (Exception e) {
                                    log.error("[ROUND_LOCKED] Auto-join failed for user {}", userId, e);
                                }
                            }
                            return null;
                        });
    }

    /**
     * Gives a round-0 pre-season registration to everyone who was around during pre-season and
     * never submitted, then chains the welcome fan-out.
     *
     * <p>Unlike {@link #processRoundLocked} this uses {@code autoRegisterDefaultTable}, which
     * writes the row a user would have produced by submitting the default table themselves — the
     * mid-season {@code NewJoin} shape would quietly cost them the guest-prediction import and
     * most of their swap allowance. See {@code .art/task_80.md}.
     */
    private void processSeasonInPlay(OutboxEvent event) throws Exception {
        SeasonInPlayPayload payload = objectMapper.readValue(event.getPayload(), SeasonInPlayPayload.class);

        Season season = seasonRepo
                .findById(payload.seasonId())
                .orElseThrow(() -> new IllegalStateException("Season not found: " + payload.seasonId()));

        autoRegisterEligibleUsers(season);

        // Always, even when nothing was auto-joined: genuine pre-season registrants are waiting on
        // this email too, and they exist independently of whether anyone needed auto-joining.
        // Written in this transaction so "auto-joins committed ⇒ recipients will be welcomed"
        // holds; processed in a later one so the fan-out gets its own retry budget.
        writeWelcomeFanoutEvent(season.getId());
    }

    private void autoRegisterEligibleUsers(Season season) {
        if (season.getPreSeasonOpensAt() == null) {
            // The enqueuer guards this, so reaching it means the season was edited in between.
            // Skip the batch rather than guess a cohort — the welcome fan-out still runs.
            log.warn("[SEASON_IN_PLAY] No preSeasonOpensAt on season {}; skipping auto-join", season.getId());
            return;
        }

        List<UUID> eligibleUserIds =
                userRepo.findUnjoinedUserIdsActiveSince(season.getId(), season.getPreSeasonOpensAt());
        if (eligibleUserIds.isEmpty()) {
            log.info("[SEASON_IN_PLAY] No users to auto-join for season {}", season.getId());
            return;
        }

        createPredictionUseCase
                .resolveMainContest(season)
                .fold(
                        error -> {
                            log.warn(
                                    "[SEASON_IN_PLAY] Skipping auto-join batch for season {}: {}",
                                    season.getId(),
                                    error);
                            return null;
                        },
                        mainContest -> {
                            int joined = 0;
                            for (UUID userId : eligibleUserIds) {
                                try {
                                    if (createPredictionUseCase
                                            .autoRegisterDefaultTable(userId, season, mainContest)
                                            .peekLeft(error -> log.warn(
                                                    "[SEASON_IN_PLAY] Auto-join skipped for user {}: {}",
                                                    userId,
                                                    error))
                                            .isRight()) {
                                        joined++;
                                    }
                                } catch (org.jooq.exception.DataAccessException
                                        | org.springframework.dao.DataAccessException e) {
                                    // See processRoundLocked: a database failure aborts the shared
                                    // transaction, so continuing would blame every later user for it.
                                    throw e;
                                } catch (Exception e) {
                                    log.error("[SEASON_IN_PLAY] Auto-join failed for user {}", userId, e);
                                }
                            }
                            log.info(
                                    "[SEASON_IN_PLAY_AUTO_JOINED] seasonId={}, eligible={}, joined={}",
                                    season.getId(),
                                    eligibleUserIds.size(),
                                    joined);
                            return null;
                        });
    }

    private void writeWelcomeFanoutEvent(UUID seasonId) throws Exception {
        OutboxEvent fanout = OutboxEvent.create(
                "season-welcome-fanout:" + seasonId,
                OutboxEventTypes.SEASON_WELCOME_FANOUT,
                "season",
                seasonId.toString(),
                objectMapper.writeValueAsString(new SeasonWelcomeFanoutPayload(seasonId)));
        outboxRepo.save(fanout);
    }

    /**
     * Second hop of the chain: expands into per-user SEASON_WELCOME events. Runs in its own
     * transaction alongside its markSent, so a crash mid-fan-out retries the whole expansion —
     * duplicate-free via the per-user idempotency keys.
     */
    private void processSeasonWelcomeFanout(OutboxEvent event) throws Exception {
        SeasonWelcomeFanoutPayload payload =
                objectMapper.readValue(event.getPayload(), SeasonWelcomeFanoutPayload.class);
        seasonWelcomeEmailEnqueuer.enqueue(payload);
    }

    private void processSeasonWelcome(OutboxEvent event) throws Exception {
        SeasonWelcomePayload payload = objectMapper.readValue(event.getPayload(), SeasonWelcomePayload.class);

        EmailContent content = emailTemplateRenderer
                .render(EmailCommand.EmailType.SEASON_WELCOME, seasonWelcomeTemplateData())
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

    private Map<String, Object> seasonWelcomeTemplateData() {
        Map<String, Object> data = new HashMap<>();
        data.put("myTableUrl", frontendUrl + "/my-table");
        data.put("leaderboardUrl", frontendUrl + "/leaderboard");
        data.put("faqUrl", frontendUrl + "/faq");
        return data;
    }

    private void processJoinReminder(OutboxEvent event) throws Exception {
        JoinReminderPayload payload = objectMapper.readValue(event.getPayload(), JoinReminderPayload.class);

        EmailContent content = emailTemplateRenderer
                .render(EmailCommand.EmailType.JOIN_REMINDER, joinReminderTemplateData(payload.stage()))
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

    private Map<String, Object> joinReminderTemplateData(int stage) {
        Map<String, Object> data = new HashMap<>();
        data.put("stage", stage);
        data.put("myTableUrl", frontendUrl + "/my-table");
        data.put("leaderboardUrl", frontendUrl + "/leaderboard");
        data.put("faqUrl", frontendUrl + "/faq");
        return data;
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
        data.put("showDetailedResultsLink", payload.round() != payload.lastRound());
        data.put(
                "detailedResultsUrl",
                frontendUrl + "/u/" + payload.userPublicId() + "/" + payload.seasonSlug() + "/gw/" + payload.round());
        return data;
    }

    /**
     * Records a failed attempt: FAILED with backoff, or DEAD_LETTER once attempts are
     * exhausted (the claim already counted the in-flight attempt).
     *
     * <p>Deliberately <em>not</em> {@code @Transactional}. When the failure was a database
     * error, {@link #processOne}'s transaction is already aborted and this write cannot
     * land from inside it — Postgres rejects every subsequent statement with
     * {@code current transaction is aborted}. {@link OutboxRelayJob} therefore calls this
     * again from outside, after the exception has crossed the transactional proxy and
     * Spring has rolled back: by then the connection is clean and the write commits on
     * its own. Adding {@code @Transactional} here would re-break that.
     */
    public void recordFailure(OutboxEvent event, Exception e) {
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
