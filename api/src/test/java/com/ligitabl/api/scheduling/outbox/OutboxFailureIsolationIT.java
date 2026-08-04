package com.ligitabl.api.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundLockedPayload;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;

/**
 * Proves the Phase 0 failure-isolation contract against a real PostgreSQL, which is the
 * only place it can be proven: the whole mechanism turns on server-side transaction-abort
 * behaviour ("current transaction is aborted, commands ignored until end of transaction
 * block") that mocks cannot reproduce.
 *
 * <p>The poison is a trigger that raises on {@code t_season_prediction} insert. That
 * stands in for "any database error mid-batch" — which is the actual precondition under
 * test — rather than depending on a specific constraint an upsert might quietly absorb
 * ({@code EntryPersistenceAdapter.save} does exactly that). Everything after the raise is
 * real production code:
 *
 * <ol>
 *   <li>{@code createPredictionAndEntry} catches the violation itself and returns
 *       {@code Either.left(TransactionFailed)} — so the loop sees no exception, but the
 *       transaction is already poisoned;
 *   <li>the <em>next</em> user's {@code resolveJoinPlan} query throws for real — this is
 *       where the narrowed catch (5c) aborts the loop instead of blaming that user;
 *   <li>{@code markSent} and then {@code recordFailure} both fail inside the dead
 *       transaction, so the exception escapes {@code processOne} (5a);
 *   <li>the relay records the failure from outside the rolled-back transaction (5b).
 * </ol>
 */
@SpringBootTest
@DisplayName("Outbox failure isolation (real Postgres)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

    /** Counts real invocations only — the use case still runs against the real database. */
    @SpyBean
    CreatePredictionUseCase createPredictionUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID competitionId;
    private UUID seasonId;
    private UUID contestId;
    private UUID roundId;

    @AfterEach
    void dropPoison() {
        jdbc.execute("DROP TRIGGER IF EXISTS it_poison_trg ON t_season_prediction");
    }

    /**
     * Any server-side error inside the batch's transaction will do; a trigger is simply the
     * most direct way to guarantee one at a known point.
     */
    private void poisonSeasonPredictionInserts() {
        jdbc.execute("CREATE OR REPLACE FUNCTION it_poison() RETURNS trigger AS $$ "
                + "BEGIN RAISE EXCEPTION 'it-poison'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER it_poison_trg BEFORE INSERT ON t_season_prediction "
                + "FOR EACH ROW EXECUTE FUNCTION it_poison()");
    }

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        insertCompetitionAndSeason();
        insertContest();
        jdbc.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);
        insertRound();
    }

    @Test
    @DisplayName("A poisoned event lands FAILED with backoff, not stranded PROCESSING")
    void poisonedEventIsMarkedFailedOutOfBand() throws Exception {
        // Three candidates, because the cascade has two distinct stages: the first user's
        // failure is swallowed inside createPredictionAndEntry (returns Either.left), the
        // second throws for real off the now-dead transaction, and the third must never be
        // attempted. Two users could not tell 5c working from 5c absent.
        insertUser("candidate-a@example.com");
        insertUser("candidate-b@example.com");
        insertUser("candidate-c@example.com");
        poisonSeasonPredictionInserts();

        outboxRepo.save(OutboxEvent.create(
                "round-locked:it-poison",
                OutboxEventTypes.ROUND_LOCKED,
                "round",
                roundId.toString(),
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, roundId, 1))));

        relayFor(outboxRepo.claimBatchForProcessing(10)).relay();

        OutboxEvent stored =
                outboxRepo.findByIdempotencyKey("round-locked:it-poison").orElseThrow();
        assertThat(stored.getStatus())
                .as("must not be left PROCESSING awaiting the 10-minute stuck sweep")
                .isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(stored.getAttempts()).isEqualTo(1);
        assertThat(stored.getLastError()).isNotBlank();

        // The whole batch rolled back, so no half-written auto-join survives.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_season_prediction", Integer.class))
                .isZero();

        // 5c: the loop stops at the user who actually threw, leaving the third candidate
        // unattempted rather than logging it as if it were at fault.
        verify(createPredictionUseCase, times(2)).executeWithContext(any(), any(), any());
    }

    @Test
    @DisplayName("A poisoned event does not strand the other events in its batch")
    void poisonedEventDoesNotStrandItsBatchMates() throws Exception {
        insertUser("candidate-d@example.com");
        insertUser("candidate-e@example.com");
        poisonSeasonPredictionInserts();

        outboxRepo.save(OutboxEvent.create(
                "round-locked:it-poison-2",
                OutboxEventTypes.ROUND_LOCKED,
                "round",
                roundId.toString(),
                objectMapper.writeValueAsString(new RoundLockedPayload(seasonId, roundId, 1))));
        outboxRepo.save(OutboxEvent.create("mystery:it-1", "MYSTERY", "round", "1", "{}"));

        List<OutboxEvent> claimed = outboxRepo.claimBatchForProcessing(10);
        assertThat(claimed).hasSize(2);

        // Pin the order so the poisoned event is processed first — otherwise the test would
        // pass trivially whenever the healthy event happened to be claimed first.
        List<OutboxEvent> poisonFirst = claimed.stream()
                .sorted((a, b) -> Boolean.compare(
                        !OutboxEventTypes.ROUND_LOCKED.equals(a.getEventType()),
                        !OutboxEventTypes.ROUND_LOCKED.equals(b.getEventType())))
                .toList();

        relayFor(poisonFirst).relay();

        assertThat(outboxRepo
                        .findByIdempotencyKey("round-locked:it-poison-2")
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(OutboxEvent.Status.FAILED);
        assertThat(outboxRepo.findByIdempotencyKey("mystery:it-1").orElseThrow().getStatus())
                .as("batch-mate claimed after the poisoned event must still be processed")
                .isEqualTo(OutboxEvent.Status.DEAD_LETTER);
    }

    /**
     * The scheduled relay bean is absent in tests (ligitabl.scheduling.enabled=false), and
     * we need the claim to return an already-claimed, deterministically ordered batch — so
     * the repo is spied for {@code claimBatchForProcessing} only. Every other call, and all
     * of the processor's work, hits the real database.
     */
    private OutboxRelayJob relayFor(List<OutboxEvent> batch) {
        OutboxRepo spied = spy(outboxRepo);
        doReturn(batch).when(spied).claimBatchForProcessing(25);
        return new OutboxRelayJob(spied, processor, clock, 25);
    }

    private void insertCompetitionAndSeason() {
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?, '[]'::jsonb, ?)",
                competitionId,
                "Premier League",
                competitionDefaults.defaultCompetitionSlug(),
                "PL",
                seasonId);

        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date,"
                        + " c_max_rounds, c_total_teams, c_initial_rankings, c_completed, fk_current_round_id,"
                        + " c_current_match_day, c_pre_season_opens_at) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                22,
                4,
                "[{\"code\":\"MCI\",\"position\":1},{\"code\":\"ARS\",\"position\":2},"
                        + "{\"code\":\"LIV\",\"position\":3},{\"code\":\"CHE\",\"position\":4}]",
                false,
                roundId,
                1,
                OffsetDateTime.now().minusDays(30));
    }

    private void insertContest() {
        jdbc.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position,"
                        + " c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestId,
                seasonId,
                "Main League",
                false,
                null,
                1,
                22,
                null);
    }

    private void insertRound() {
        jdbc.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) VALUES (?,?,?,?,?,?)",
                roundId,
                seasonId,
                "Round 1",
                "round-1",
                1,
                false);
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                id,
                email,
                "test-password-hash",
                "Test User",
                UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                true);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
        return id;
    }
}
