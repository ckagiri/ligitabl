package com.ligitabl.api.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.SeasonInPlayPayload;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.InPlaySeasonFixture;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

/**
 * The three-hop auto-join chain end to end, against a real database:
 *
 * <pre>
 * SEASON_IN_PLAY  →  auto-join + write one chain event      [tx 1]
 * SEASON_WELCOME_FANOUT  →  one event per recipient          [tx 2]
 * SEASON_WELCOME  →  render + send                           [tx 3..]
 * </pre>
 *
 * <p>Split from {@code OutboxFailureIsolationIT}, which shares the fixture but tests the opposite
 * question. Keeping them together meant a class-wide {@code @SpyBean} — and its separate Spring
 * context — for tests that never needed one, under a name that described half its contents.
 */
@SpringBootTest
@DisplayName("Season in-play auto-join chain (real Postgres)")
class SeasonInPlayChainIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxRepo outboxRepo;

    @Autowired
    OutboxEventProcessor processor;

    @Autowired
    Clock clock;

    @Autowired
    CompetitionDefaults competitionDefaults;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InPlaySeasonFixture season;

    @BeforeEach
    void setup() {
        season = InPlaySeasonFixture.createFresh(jdbc, competitionDefaults.defaultCompetitionSlug());
    }

    private void enqueueSeasonInPlay(String key) throws Exception {
        outboxRepo.save(OutboxEvent.create(
                key,
                OutboxEventTypes.SEASON_IN_PLAY,
                "season",
                season.seasonId.toString(),
                objectMapper.writeValueAsString(new SeasonInPlayPayload(season.seasonId))));
    }

    /** One relay tick. The scheduled bean is absent in tests, so the job is driven by hand. */
    private void relayOnce() {
        OutboxRepo spied = spy(outboxRepo);
        doReturn(outboxRepo.claimBatchForProcessing(25)).when(spied).claimBatchForProcessing(25);
        new OutboxRelayJob(spied, processor, clock, 25).relay();
    }

    private OutboxEvent stored(String key) {
        return outboxRepo.findByIdempotencyKey(key).orElseThrow();
    }

    private String fanoutKey() {
        return "season-welcome-fanout:" + season.seasonId;
    }

    private String welcomeKey(UUID userId) {
        return "season-welcome:%s:%s".formatted(userId, season.seasonId);
    }

    private int countEventsOfType(String type) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM t_outbox_event WHERE c_event_type = ?", Integer.class, type);
    }

    @Nested
    @DisplayName("Hop 1 — auto-join")
    class AutoJoin {

        @Test
        @DisplayName("writes a pre-season registration, not a mid-season join")
        void writesRoundZeroRegistration() throws Exception {
            UUID user = season.insertUser("returning@example.com");

            enqueueSeasonInPlay("season-in-play:shape");
            relayOnce();

            assertThat(stored("season-in-play:shape").getStatus()).isEqualTo(OutboxEvent.Status.SENT);

            // The shape is the entire premise: asserting only that "a row exists" would pass for
            // the NewJoin shape too, which silently costs the user most of their swap allowance.
            assertThat(jdbc.queryForObject(
                            "SELECT c_at_round_number FROM t_season_prediction WHERE fk_user_id = ?",
                            Integer.class,
                            user))
                    .as("round 0, so the user keeps the full merge allowance")
                    .isZero();
            assertThat(jdbc.queryForObject(
                            "SELECT c_initial_rankings IS NOT NULL FROM t_season_prediction WHERE fk_user_id = ?",
                            Boolean.class,
                            user))
                    .as("the permanent marker; without it the later merge reports corrupt")
                    .isTrue();
            assertThat(jdbc.queryForObject(
                            "SELECT c_last_swap_at IS NULL FROM t_season_prediction WHERE fk_user_id = ?",
                            Boolean.class,
                            user))
                    .as("no swaps used, so the first-swap bonus survives")
                    .isTrue();
            assertThat(jdbc.queryForObject(
                            "SELECT c_joined_at_round FROM t_entry WHERE fk_user_id = ?", Integer.class, user))
                    .isZero();
        }

        @Test
        @DisplayName("chains the fan-out even when nobody needed auto-joining")
        void chainsTheFanoutWithNoEligibleUsers() throws Exception {
            // No users at all. processRoundLocked returns early on an empty candidate list;
            // copying that here would silently skip welcoming genuine pre-season registrants,
            // who exist regardless of whether anyone needed auto-joining.
            enqueueSeasonInPlay("season-in-play:empty");
            relayOnce();

            assertThat(stored("season-in-play:empty").getStatus()).isEqualTo(OutboxEvent.Status.SENT);
            assertThat(outboxRepo.findByIdempotencyKey(fanoutKey())).isPresent();
        }

        @Test
        @DisplayName("writes one chain event for the season, not one per user")
        void writesASingleChainEvent() throws Exception {
            season.insertUser("a@example.com");
            season.insertUser("b@example.com");
            season.insertUser("c@example.com");

            enqueueSeasonInPlay("season-in-play:single-chain");
            relayOnce();

            assertThat(countEventsOfType(OutboxEventTypes.SEASON_WELCOME_FANOUT)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Full chain")
    class FullChain {

        @Test
        @DisplayName("reaches a per-user welcome that renders and sends")
        void reachesAPerUserWelcomeEmail() throws Exception {
            UUID user = season.insertUser("chain@example.com");

            enqueueSeasonInPlay("season-in-play:chain");
            relayOnce(); // hop 1: auto-join + chain event
            relayOnce(); // hop 2: fan out to per-user events
            relayOnce(); // hop 3: render + send

            assertThat(stored(fanoutKey()).getStatus()).isEqualTo(OutboxEvent.Status.SENT);
            assertThat(stored(welcomeKey(user)).getStatus())
                    .as("template must render and the provider must accept it")
                    .isEqualTo(OutboxEvent.Status.SENT);
        }

        @Test
        @DisplayName("is idempotent — re-running produces no second welcome")
        void reRunningProducesNoDuplicateWelcome() throws Exception {
            season.insertUser("chain@example.com");

            enqueueSeasonInPlay("season-in-play:idempotent");
            relayOnce();
            relayOnce();
            relayOnce();

            assertThat(countEventsOfType(OutboxEventTypes.SEASON_WELCOME)).isEqualTo(1);

            // Idempotency across the whole pipeline, not just one event: every key is re-derived
            // from the same season and user, so a second pass must collide on all of them.
            relayOnce();
            relayOnce();
            relayOnce();

            assertThat(countEventsOfType(OutboxEventTypes.SEASON_WELCOME)).isEqualTo(1);
            assertThat(countEventsOfType(OutboxEventTypes.SEASON_WELCOME_FANOUT)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM t_season_prediction", Integer.class))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("skips a user who opted out of email, but still gives them a table")
        void optedOutUserIsAutoJoinedButNotEmailed() throws Exception {
            // The deliberate asymmetry between the two queries: opting out of email must not cost
            // someone their place in the season.
            UUID optedOut = season.insertUser("optout@example.com");
            jdbc.update("UPDATE t_user SET c_results_email_opt_out = true WHERE pk_id = ?", optedOut);

            enqueueSeasonInPlay("season-in-play:optout");
            relayOnce();
            relayOnce();

            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM t_season_prediction WHERE fk_user_id = ?", Integer.class, optedOut))
                    .as("auto-join ignores email preferences")
                    .isEqualTo(1);
            assertThat(outboxRepo.findByIdempotencyKey(welcomeKey(optedOut)))
                    .as("the welcome does not")
                    .isEmpty();
        }
    }
}
