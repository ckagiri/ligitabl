package com.ligitabl.api.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import com.ligitabl.api.notification.outbox.RoundAdvancedPayload;
import com.ligitabl.api.notification.outbox.SeasonCompletedPayload;
import com.ligitabl.api.notification.outbox.SegmentResultsPayload;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.api.testsupport.TestIds;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSubmission;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

/**
 * The segment-success chain end to end against a real database:
 *
 * <pre>
 * ROUND_ADVANCED    →  one SEGMENT_RESULTS per podium finisher, dated +delay   [tx 1]
 * SEASON_COMPLETED  →  the same, for the full-season podium                    [tx 1']
 * SEGMENT_RESULTS   →  render + send                                           [tx 2..]
 * </pre>
 *
 * <p>Its own fixture rather than {@link com.ligitabl.api.testsupport.InPlaySeasonFixture}: that one
 * writes {@code c_phases = '[]'} and a single round, and every question here is about phase
 * boundaries and scored leaderboards. The season is deliberately tiny — 9 rounds, two sprints, one
 * quarter — so the two boundary shapes sit at rounds 4 and 9:
 *
 * <table>
 *   <tr><th>round</th><th>closes</th></tr>
 *   <tr><td>4</td><td>S1 only</td></tr>
 *   <tr><td>5–8</td><td>nothing</td></tr>
 *   <tr><td>9</td><td>S2 <em>and</em> Q1</td></tr>
 * </table>
 *
 * <p>What only a real database can show: that the leaderboard actually resolves over each
 * segment's own window, that the delayed events sit unclaimed in PENDING, and that the round-38
 * scope-key collision is genuinely avoided at the unique index rather than merely in the string
 * we build.
 */
@SpringBootTest
@DisplayName("Segment-results chain (real Postgres)")
class SegmentResultsChainIT extends AbstractPostgresIT {

    private static final String PHASES =
            """
            [{"code":"FS","name":"Season","from":1,"to":9,"type":"FULL_SEASON"},
             {"code":"Q1","name":"Quarter 1","from":1,"to":9,"type":"QUARTER"},
             {"code":"S1","name":"Sprint 1","from":1,"to":4,"type":"SPRINT"},
             {"code":"S2","name":"Sprint 2","from":5,"to":9,"type":"SPRINT"}]
            """;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxRepo outboxRepo;

    @Autowired
    OutboxEventProcessor processor;

    @Autowired
    CompetitionDefaults competitionDefaults;

    @Autowired
    SeasonPredictionRepo seasonPredictionRepo;

    @Autowired
    RoundSubmissionRepo roundSubmissionRepo;

    @Autowired
    RoundResultRepo roundResultRepo;

    @Autowired
    EntryRepo entryRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID competitionId = UUID.randomUUID();
    private final UUID seasonId = UUID.randomUUID();
    private final UUID contestId = UUID.randomUUID();

    private UUID alice;
    private UUID bob;
    private UUID carol;
    private UUID dave;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        insertCompetitionSeasonAndContest();

        alice = insertUser("alice@x.com", "Alice");
        bob = insertUser("bob@x.com", "Bob");
        carol = insertUser("carol@x.com", "Carol");
        dave = insertUser("dave@x.com", "Dave");

        // The scores are chosen so the two podiums at round 9 are in genuinely different orders,
        // and each shape is represented. Sprint 1 dwarfs sprint 2 so the quarter aggregate is
        // dominated by round 1:
        //
        //   sprint 1 (1–4)   alice 1000 · bob 900 · carol 800 · dave 0
        //   sprint 2 (5–9)   dave  100 · carol 50 · bob   10 · alice 5
        //   quarter  (1–9)   alice 1005 · bob 910 · carol 850 · dave 100
        //
        //   round 4 podium   alice, bob, carol          (dave off)
        //   S2 podium        dave(1), carol(2), bob(3)  (alice off)
        //   Q1 podium        alice(1), bob(2), carol(3) (dave off)
        //
        // So at round 9: dave is sprint-only, alice is quarter-only, bob and carol take both. A
        // board read over the wrong window would rank people visibly wrongly rather than landing
        // on coincidentally identical answers.
        score(alice, 1, 1000);
        score(bob, 1, 900);
        score(carol, 1, 800);
        score(dave, 1, 0);

        score(dave, 9, 100);
        score(carol, 9, 50);
        score(bob, 9, 10);
        score(alice, 9, 5);
    }

    // ------------------------------------------------------------------ fixture

    private void insertCompetitionSeasonAndContest() {
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id)"
                        + " VALUES (?,?,?,?,?::jsonb,?)",
                competitionId,
                "Premier League",
                competitionDefaults.defaultCompetitionSlug(),
                "PL",
                PHASES,
                seasonId);

        // fk_main_contest_id is set by the UPDATE below, not here: the contest does not exist yet
        // and the FK is enforced.
        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date,"
                        + " c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed,"
                        + " c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                9,
                4,
                "[]",
                false,
                1);

        jdbc.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code,"
                        + " c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestId,
                seasonId,
                "Main League",
                false,
                null,
                1,
                9,
                null);
        jdbc.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id,"
                        + " c_email_verified, c_results_email_opt_out) VALUES (?,?,?,?,?,?,?)",
                id,
                email,
                "test-password-hash",
                displayName,
                TestIds.randomPublicId(),
                true,
                false);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");

        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(id)
                .seasonId(seasonId)
                .initialRankings(List.of())
                .currentRankings(List.of())
                .swaps(List.of())
                .atRoundNumber(1)
                .build();
        seasonPredictionRepo.save(prediction);
        predictionIds.put(id, prediction.getId());

        entryRepo.save(Entry.builder()
                .userId(id)
                .contestId(contestId)
                .joinedAtRound(1)
                .build());
        return id;
    }

    private final Map<UUID, UUID> predictionIds = new java.util.HashMap<>();

    private void score(UUID userId, int roundPosition, int score) {
        ensureAdvancedRound(roundPosition);
        RoundSubmission submission = roundSubmissionRepo.save(RoundSubmission.builder()
                .userId(userId)
                .seasonId(seasonId)
                .roundPosition(roundPosition)
                .rankings(List.<TeamRank>of())
                .seasonPredictionId(predictionIds.get(userId))
                .build());

        roundResultRepo.save(RoundResult.builder()
                .id(UUID.randomUUID())
                .roundSubmissionId(submission.getId())
                .rankings(List.<ResultTeamRank>of())
                .totalScore(score)
                .zeroesCount(0)
                .swapCount(0)
                .userViewed(false)
                .build());
    }

    private void ensureAdvancedRound(int roundPosition) {
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM t_round WHERE fk_season_id = ? AND c_position = ?",
                Integer.class,
                seasonId,
                roundPosition);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized, c_advanced)"
                        + " VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                seasonId,
                "Round " + roundPosition,
                "round-" + roundPosition,
                roundPosition,
                true,
                true);
    }

    // ------------------------------------------------------------------ driving

    private void advance(int roundPosition) throws Exception {
        OutboxEvent event = OutboxEvent.create(
                "round-advanced:%s:%d".formatted(seasonId, roundPosition),
                OutboxEventTypes.ROUND_ADVANCED,
                "round",
                String.valueOf(roundPosition),
                objectMapper.writeValueAsString(
                        new RoundAdvancedPayload(seasonId, roundPosition, roundPosition + 1)));
        outboxRepo.save(event);
        processor.processOne(event);
    }

    private void completeSeason() throws Exception {
        OutboxEvent event = OutboxEvent.create(
                "season-completed:" + seasonId,
                OutboxEventTypes.SEASON_COMPLETED,
                "season",
                seasonId.toString(),
                objectMapper.writeValueAsString(new SeasonCompletedPayload(seasonId)));
        outboxRepo.save(event);
        processor.processOne(event);
    }

    private List<SegmentResultsPayload> segmentEvents() {
        return jdbc
                .queryForList(
                        "SELECT c_payload::text AS p FROM t_outbox_event WHERE c_event_type = ?"
                                + " ORDER BY c_created_at, pk_id",
                        OutboxEventTypes.SEGMENT_RESULTS)
                .stream()
                .map(row -> {
                    try {
                        return objectMapper.readValue((String) row.get("p"), SegmentResultsPayload.class);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    private SegmentResultsPayload eventFor(UUID userId) {
        return segmentEvents().stream()
                .filter(p -> p.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no segment event for " + userId));
    }

    private int countSegmentEvents() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM t_outbox_event WHERE c_event_type = ?",
                Integer.class,
                OutboxEventTypes.SEGMENT_RESULTS);
        return n == null ? 0 : n;
    }

    // ----------------------------------------------------------------- the tests

    @Nested
    @DisplayName("Boundaries")
    class Boundaries {

        @Test
        @DisplayName("a sprint-only boundary emails the sprint podium and nobody else")
        void sprintOnlyBoundary() throws Exception {
            advance(4);

            assertThat(segmentEvents()).hasSize(3);
            assertThat(segmentEvents()).extracting(SegmentResultsPayload::userId).doesNotContain(dave);

            SegmentResultsPayload payload = eventFor(alice);
            assertThat(payload.scopeKey()).isEqualTo("r4");
            assertThat(payload.placements()).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo("S1");
                assertThat(p.rank()).isEqualTo(1);
                assertThat(p.totalScore()).isEqualTo(1000);
                assertThat(p.totalParticipants()).isEqualTo(4);
            });
        }

        @Test
        @DisplayName("a mid-sprint round writes nothing at all")
        void midSprintBoundary() throws Exception {
            advance(5);
            advance(6);
            advance(7);

            assertThat(countSegmentEvents()).isZero();
        }

        /**
         * The reason the fixture gives sprint 2 and the quarter different orderings: reading either
         * board over the other's window would produce visibly wrong ranks here, not coincidentally
         * identical ones.
         */
        @Test
        @DisplayName("a sprint+quarter boundary resolves each over its own window")
        void combinedBoundaryUsesPerSegmentWindows() throws Exception {
            advance(9);

            SegmentResultsPayload carolPayload = eventFor(carol);
            assertThat(carolPayload.placements())
                    .extracting(SegmentResultsPayload.SegmentPlacement::code)
                    .containsExactly("S2", "Q1");

            assertThat(carolPayload.placements().get(0)).satisfies(sprint -> {
                assertThat(sprint.rank()).as("2nd over rounds 5–9").isEqualTo(2);
                assertThat(sprint.totalScore()).isEqualTo(50);
                assertThat(sprint.fromRound()).isEqualTo(5);
            });
            assertThat(carolPayload.placements().get(1)).satisfies(quarter -> {
                assertThat(quarter.rank())
                        .as("but 3rd over rounds 1–9 — the same person, two different standings")
                        .isEqualTo(3);
                assertThat(quarter.totalScore()).isEqualTo(850);
                assertThat(quarter.fromRound()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("a user on both podiums receives exactly one email")
        void doubleFinisherGetsOneEmail() throws Exception {
            advance(9);

            assertThat(segmentEvents().stream().filter(p -> p.userId().equals(carol)))
                    .hasSize(1);
            assertThat(segmentEvents())
                    .as("dave + alice (one podium each) and bob + carol (both) — four people, four emails")
                    .hasSize(4);
            assertThat(segmentEvents()).allSatisfy(p -> assertThat(p.placements())
                    .isNotEmpty());
        }

        @Test
        @DisplayName("a user on only one podium gets only that block")
        void singlePodiumUserGetsOneBlock() throws Exception {
            advance(9);

            assertThat(eventFor(dave).placements())
                    .as("dave won sprint 2 but sat 4th on the quarter aggregate")
                    .extracting(SegmentResultsPayload.SegmentPlacement::code)
                    .containsExactly("S2");
            assertThat(eventFor(alice).placements())
                    .as("alice was last in sprint 2 but her round-1 haul still leads the quarter")
                    .extracting(SegmentResultsPayload.SegmentPlacement::code)
                    .containsExactly("Q1");
        }
    }

    @Nested
    @DisplayName("Delivery")
    class Delivery {

        @Test
        @DisplayName("events are written immediately but not claimable until the delay passes")
        void heldBackByTheDelay() throws Exception {
            advance(4);

            // Written now, with the standings already resolved — the delay is delivery-only.
            assertThat(countSegmentEvents()).isEqualTo(3);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM t_outbox_event WHERE c_event_type = ?"
                                    + " AND c_status = 'PENDING' AND c_available_at > now() + interval '23 hours'",
                            Integer.class,
                            OutboxEventTypes.SEGMENT_RESULTS))
                    .isEqualTo(3);

            assertThat(outboxRepo.claimBatchForProcessing(50))
                    .as("the relay must not pick them up on this poll")
                    .noneMatch(e -> OutboxEventTypes.SEGMENT_RESULTS.equals(e.getEventType()));
        }

        @Test
        @DisplayName("once due, a segment event renders and sends")
        void rendersAndSendsOnceDue() throws Exception {
            advance(4);
            jdbc.update(
                    "UPDATE t_outbox_event SET c_available_at = now() - interval '1 minute' WHERE c_event_type = ?",
                    OutboxEventTypes.SEGMENT_RESULTS);

            List<OutboxEvent> claimed = outboxRepo.claimBatchForProcessing(50).stream()
                    .filter(e -> OutboxEventTypes.SEGMENT_RESULTS.equals(e.getEventType()))
                    .toList();
            assertThat(claimed).hasSize(3);
            claimed.forEach(processor::processOne);

            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM t_outbox_event WHERE c_event_type = ? AND c_status = 'SENT'",
                            Integer.class,
                            OutboxEventTypes.SEGMENT_RESULTS))
                    .isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Season completion")
    class SeasonCompletion {

        /**
         * The collision the scope key exists for. Both fan-outs cover round 9 and the same people;
         * a key without the scope would be rejected by the unique index and the champion would
         * never be told. This is the one assertion that has to run against a real index rather
         * than against the string we build.
         */
        @Test
        @DisplayName("the season podium survives alongside the round-9 boundary email")
        void seasonPodiumDoesNotCollideWithTheRoundBoundary() throws Exception {
            advance(9);
            int afterRound = countSegmentEvents();

            completeSeason();

            assertThat(countSegmentEvents())
                    .as("season events are added, not swallowed by the round-9 keys")
                    .isGreaterThan(afterRound);
            assertThat(jdbc.queryForList(
                            "SELECT c_idempotency_key FROM t_outbox_event WHERE c_event_type = ?"
                                    + " AND c_idempotency_key LIKE '%:season:%'",
                            String.class,
                            OutboxEventTypes.SEGMENT_RESULTS))
                    .isNotEmpty();
        }

        @Test
        @DisplayName("the season email reports the full-season window")
        void seasonEmailReportsTheSeason() throws Exception {
            completeSeason();

            SegmentResultsPayload payload = eventFor(alice);
            assertThat(payload.scopeKey()).isEqualTo("season");
            assertThat(payload.boundaryRound()).isEqualTo(9);
            assertThat(payload.placements()).singleElement().satisfies(p -> {
                assertThat(p.type()).isEqualTo("FULL_SEASON");
                assertThat(p.fromRound()).isEqualTo(1);
                assertThat(p.toRound()).isEqualTo(9);
                assertThat(p.rank()).isEqualTo(1);
                assertThat(p.totalScore()).isEqualTo(1005);
            });
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("replaying every hop produces no second email")
        void replayIsFree() throws Exception {
            advance(4);
            advance(9);
            completeSeason();
            int afterFirstPass = countSegmentEvents();

            // A relay retry re-runs the whole expansion; the per-user keys are what make that safe.
            processor.processOne(outboxRepo
                    .findByIdempotencyKey("round-advanced:%s:%d".formatted(seasonId, 4))
                    .orElseThrow());
            processor.processOne(outboxRepo
                    .findByIdempotencyKey("round-advanced:%s:%d".formatted(seasonId, 9))
                    .orElseThrow());
            processor.processOne(
                    outboxRepo.findByIdempotencyKey("season-completed:" + seasonId).orElseThrow());

            assertThat(countSegmentEvents()).isEqualTo(afterFirstPass);
        }

        @Test
        @DisplayName("a replay does not move an already-scheduled event's delivery time")
        void replayDoesNotRescheduleDelivery() throws Exception {
            advance(4);
            Instant scheduled = outboxRepo
                    .findByIdempotencyKey("segment-results:%s:r4:%s".formatted(seasonId, alice))
                    .orElseThrow()
                    .getAvailableAt();

            processor.processOne(outboxRepo
                    .findByIdempotencyKey("round-advanced:%s:%d".formatted(seasonId, 4))
                    .orElseThrow());

            assertThat(outboxRepo
                            .findByIdempotencyKey("segment-results:%s:r4:%s".formatted(seasonId, alice))
                            .orElseThrow()
                            .getAvailableAt())
                    .as("ON CONFLICT DO NOTHING means the second insert cannot push delivery out another day")
                    .isCloseTo(scheduled, org.assertj.core.api.Assertions.within(Duration.ofMillis(1)));
        }
    }
}
