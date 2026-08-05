package com.ligitabl.api.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundLockedPayload;
import com.ligitabl.api.notification.outbox.SeasonInPlayPayload;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.InPlaySeasonFixture;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

/**
 * What happens to the outbox when a batch dies mid-flight.
 *
 * <p>Only provable against a real PostgreSQL: the whole mechanism turns on server-side
 * transaction-abort behaviour — {@code current transaction is aborted, commands ignored until
 * end of transaction block} — which mocks cannot reproduce. A unit test can assert the wiring;
 * only this can assert the guarantee.
 *
 * <p>The poison is a trigger that raises on {@code t_season_prediction} insert, standing in for
 * "any database error mid-batch", which is the real precondition. An earlier attempt used a
 * genuine unique-constraint violation and proved nothing, because
 * {@code EntryPersistenceAdapter.save} is an upsert and quietly absorbed it. Everything after
 * the raise is production code:
 *
 * <ol>
 *   <li>the auto-join catches the error itself and returns {@code Either.left(TransactionFailed)},
 *       so the loop sees no exception — but the transaction is already dead;
 *   <li>the <em>next</em> user's query throws for real, and the narrowed catch stops the loop
 *       there instead of blaming that user;
 *   <li>{@code markSent}, then {@code recordFailure}, both fail inside the dead transaction, so
 *       the exception escapes {@code processOne};
 *   <li>{@link OutboxRelayJob} records the failure from outside the rolled-back transaction.
 * </ol>
 */
@SpringBootTest
@DisplayName("Outbox failure isolation (real Postgres)")
class OutboxFailureIsolationIT extends AbstractPostgresIT {

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

    /**
     * Counts real invocations only — the use case still runs against the real database. Needed to
     * observe that the loop <em>stopped</em>, which no database state can show.
     */
    @SpyBean
    CreatePredictionUseCase createPredictionUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InPlaySeasonFixture season;

    @BeforeEach
    void setup() {
        season = InPlaySeasonFixture.createFresh(jdbc, competitionDefaults.defaultCompetitionSlug());
    }

    @AfterEach
    void dropPoison() {
        jdbc.execute("DROP TRIGGER IF EXISTS it_poison_trg ON t_season_prediction");
    }

    /** Any server-side error inside the batch's transaction will do; a trigger just guarantees one. */
    private void poisonSeasonPredictionInserts() {
        jdbc.execute("CREATE OR REPLACE FUNCTION it_poison() RETURNS trigger AS $$ "
                + "BEGIN RAISE EXCEPTION 'it-poison'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER it_poison_trg BEFORE INSERT ON t_season_prediction "
                + "FOR EACH ROW EXECUTE FUNCTION it_poison()");
    }

    private OutboxEvent saveEvent(String key, String type, String payload) {
        OutboxEvent event = OutboxEvent.create(key, type, "season", season.seasonId.toString(), payload);
        outboxRepo.save(event);
        return event;
    }

    private String roundLockedPayload() throws Exception {
        return objectMapper.writeValueAsString(new RoundLockedPayload(season.seasonId, season.roundId, 1));
    }

    private String seasonInPlayPayload() throws Exception {
        return objectMapper.writeValueAsString(new SeasonInPlayPayload(season.seasonId));
    }

    private OutboxEvent stored(String key) {
        return outboxRepo.findByIdempotencyKey(key).orElseThrow();
    }

    private int countPredictions() {
        return jdbc.queryForObject("SELECT count(*) FROM t_season_prediction", Integer.class);
    }

    /**
     * The scheduled relay bean is absent in tests ({@code ligitabl.scheduling.enabled=false}), and
     * the batch order has to be deterministic — so the repo is spied for
     * {@code claimBatchForProcessing} only. Every other call, and all of the processor's work,
     * hits the real database.
     */
    private void relay(List<OutboxEvent> batch) {
        OutboxRepo spied = spy(outboxRepo);
        doReturn(batch).when(spied).claimBatchForProcessing(25);
        new OutboxRelayJob(spied, processor, clock, 25).relay();
    }

    private void relayClaimed() {
        relay(outboxRepo.claimBatchForProcessing(10));
    }

    @Test
    @DisplayName("A poisoned event lands FAILED with backoff rather than stranded PROCESSING")
    void poisonedEventIsMarkedFailedOutOfBand() throws Exception {
        // Three candidates, because the cascade has two distinct stages: the first user's failure
        // is swallowed inside createPredictionAndEntry, the second throws for real off the dead
        // transaction, and the third must never be attempted. With two users the call count is
        // identical whether the loop stops or not, so the test would prove nothing.
        season.insertUser("first@example.com");
        season.insertUser("second@example.com");
        season.insertUser("third@example.com");
        poisonSeasonPredictionInserts();

        saveEvent("round-locked:poison", OutboxEventTypes.ROUND_LOCKED, roundLockedPayload());
        relayClaimed();

        OutboxEvent event = stored("round-locked:poison");
        assertThat(event.getStatus())
                .as("must not be left PROCESSING awaiting the 10-minute stuck sweep")
                .isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNotBlank();

        assertThat(countPredictions())
                .as("the whole batch rolled back, so no half-written auto-join survives")
                .isZero();

        // The loop stopped at the user who actually threw: the third was never attempted, which
        // no database state can show because nothing was written either way.
        verify(createPredictionUseCase, times(2)).executeWithContext(any(), any(), any());
    }

    @Test
    @DisplayName("A poisoned event does not strand the rest of its claimed batch")
    void poisonedEventDoesNotStrandItsBatchMates() throws Exception {
        season.insertUser("first@example.com");
        season.insertUser("second@example.com");
        poisonSeasonPredictionInserts();

        saveEvent("round-locked:poison", OutboxEventTypes.ROUND_LOCKED, roundLockedPayload());
        saveEvent("mystery:1", "MYSTERY", "{}");

        List<OutboxEvent> claimed = outboxRepo.claimBatchForProcessing(10);
        assertThat(claimed).hasSize(2);

        // Order is pinned rather than assumed: RETURNING makes no ordering guarantee, and if the
        // healthy event happened to run first the test would pass without the guard.
        List<OutboxEvent> poisonFirst = claimed.stream()
                .sorted((a, b) -> Boolean.compare(
                        !OutboxEventTypes.ROUND_LOCKED.equals(a.getEventType()),
                        !OutboxEventTypes.ROUND_LOCKED.equals(b.getEventType())))
                .toList();

        relay(poisonFirst);

        assertThat(stored("round-locked:poison").getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(stored("mystery:1").getStatus())
                .as("claimed after the poisoned event, so it would be lost without the relay guard")
                .isEqualTo(OutboxEvent.Status.DEAD_LETTER);
    }

    @Test
    @DisplayName("A poisoned SEASON_IN_PLAY rolls back its auto-joins and its chain event together")
    void poisonedSeasonInPlayRollsBackTheChainEventToo() throws Exception {
        // The guarantee the three-hop design rests on. A half-state — nobody auto-joined but
        // everyone queued to be welcomed — is the one outcome that cannot be recovered from,
        // because the welcome would tell users about a table they do not have.
        season.insertUser("first@example.com");
        season.insertUser("second@example.com");
        season.insertUser("third@example.com");
        poisonSeasonPredictionInserts();

        saveEvent("season-in-play:poison", OutboxEventTypes.SEASON_IN_PLAY, seasonInPlayPayload());
        relayClaimed();

        assertThat(stored("season-in-play:poison").getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(countPredictions()).isZero();
        assertThat(outboxRepo.findByIdempotencyKey("season-welcome-fanout:" + season.seasonId))
                .as("nobody was auto-joined, so nobody may be queued to be welcomed")
                .isEmpty();
    }

    @Test
    @DisplayName("A retry after the poison clears succeeds, because nothing was half-written")
    void retryAfterPoisonClearsSucceeds() throws Exception {
        // The other half of "it rolls back": rolling back is only useful if the retry then works.
        UUID user = season.insertUser("retrying@example.com");
        poisonSeasonPredictionInserts();

        saveEvent("season-in-play:retry", OutboxEventTypes.SEASON_IN_PLAY, seasonInPlayPayload());
        relayClaimed();
        assertThat(stored("season-in-play:retry").getStatus()).isEqualTo(OutboxEvent.Status.FAILED);

        dropPoison();
        // markFailed pushes availability into the future, so re-claim by hand rather than wait.
        relay(List.of(stored("season-in-play:retry")));

        assertThat(stored("season-in-play:retry").getStatus()).isEqualTo(OutboxEvent.Status.SENT);
        assertThat(jdbc.queryForObject(
                        "SELECT c_at_round_number FROM t_season_prediction WHERE fk_user_id = ?",
                        Integer.class,
                        user))
                .isZero();
    }
}
