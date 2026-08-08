package com.ligitabl.model.repo;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
import static com.ligitabl.model.db.tables.TFinalTablePrediction.T_FINAL_TABLE_PREDICTION;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
import static com.ligitabl.model.db.tables.TUser.T_USER;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.FinalTableLeaderboardEntry;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.infra.FinalTablePredictionPersistenceAdapter;

/**
 * Tagged, and deliberately NOT named *IT: the model module signals DB tests by tag, so a model
 * test named *IT silently never runs.
 */
@Tag("integration")
class FinalTablePredictionRepoTest {

    private static final Instant BASE = Instant.parse("2026-08-01T10:00:00Z");

    private static Connection jdbc;
    private static DSLContext dsl;
    private static FinalTablePredictionRepo repo;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private UUID seasonId;
    private String runSuffix;

    @BeforeAll
    static void setup() throws Exception {
        jdbc = TestDbConnections.open();
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new FinalTablePredictionPersistenceAdapter(dsl);

        TestDbCleaner.truncatePublicTables(dsl);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    @BeforeEach
    void freshSeason() {
        dsl.deleteFrom(T_FINAL_TABLE_PREDICTION).execute();
        runSuffix = String.valueOf(SEQ.incrementAndGet());
        seasonId = insertSeason();
    }

    private UUID insertSeason() {
        UUID competitionId = UUID.randomUUID();
        int n = SEQ.incrementAndGet();
        dsl.insertInto(T_COMPETITION)
                .set(T_COMPETITION.PK_ID, competitionId)
                .set(T_COMPETITION.C_NAME, "Comp " + n)
                .set(T_COMPETITION.C_SLUG, "comp-" + n)
                .set(T_COMPETITION.C_CODE, "C" + n)
                .execute();

        UUID id = UUID.randomUUID();
        dsl.insertInto(T_SEASON)
                .set(T_SEASON.PK_ID, id)
                .set(T_SEASON.C_CLIENT_ID, n)
                .set(T_SEASON.FK_COMPETITION_ID, competitionId)
                .set(T_SEASON.C_NAME, "Season " + n)
                .set(T_SEASON.C_SLUG, "season-" + n)
                .set(T_SEASON.C_START_DATE, LocalDate.of(2026, 8, 1))
                .set(T_SEASON.C_END_DATE, LocalDate.of(2027, 5, 30))
                .set(T_SEASON.C_MAX_ROUNDS, 38)
                .set(T_SEASON.C_CURRENT_MATCH_DAY, 1)
                .set(T_SEASON.C_TOTAL_TEAMS, 20)
                .set(T_SEASON.C_MAX_HIT_POINTS, 200)
                .execute();
        return id;
    }

    /**
     * Public IDs are globally unique, so they are suffixed per test run — {@link #publicId} maps a
     * test-local label back to the stored value for assertions.
     */
    private String publicId(String label) {
        // c_public_id is varchar(10), so keep the label short enough to survive the suffix.
        String suffixed = label + "-" + runSuffix;
        return suffixed.length() <= 10 ? suffixed : suffixed.substring(suffixed.length() - 10);
    }

    private UUID insertUser(String label) {
        UUID userId = UUID.randomUUID();
        dsl.insertInto(T_USER)
                .set(T_USER.PK_ID, userId)
                .set(T_USER.C_EMAIL, "user-" + userId + "@example.com")
                .set(T_USER.C_PUBLIC_ID, publicId(label))
                .set(T_USER.C_DISPLAY_NAME, "Player " + label)
                .execute();
        return userId;
    }

    private static List<TeamRank> rankings() {
        return List.of(TeamRank.of("ARS", 1), TeamRank.of("CHE", 2), TeamRank.of("LIV", 3));
    }

    private FinalTablePrediction newRow(UUID userId, Instant settledAt) {
        return FinalTablePrediction.builder()
                .userId(userId)
                .seasonId(seasonId)
                .rankings(rankings())
                .settledAt(settledAt)
                .build();
    }

    /** Scores a row and forces its create date, so determinism-key assertions are reproducible. */
    private FinalTablePrediction saveScored(
            String publicId, int totalScore, int zeroes, Instant settledAt, Instant createDate) {
        UUID userId = insertUser(publicId);
        FinalTablePrediction row = newRow(userId, settledAt);
        row.setBaseScore(totalScore - zeroes * 10);
        row.setZeroesCount(zeroes);
        row.setBonusPoints(zeroes * 10);
        row.setTotalScore(totalScore);
        row.setScoredAt(BASE.plusSeconds(999_999));
        FinalTablePrediction saved = repo.save(row);

        if (createDate != null) {
            dsl.update(T_FINAL_TABLE_PREDICTION)
                    .set(T_FINAL_TABLE_PREDICTION.C_CREATE_DATE, OffsetDateTime.ofInstant(createDate, ZoneOffset.UTC))
                    .where(T_FINAL_TABLE_PREDICTION.PK_ID.eq(saved.getId()))
                    .execute();
        }
        return saved;
    }

    @Test
    void savesAndReadsBackRoundTrip() {
        UUID userId = insertUser("u1");
        FinalTablePrediction row = newRow(userId, BASE);
        row.addSwap(new SwapChange(BASE.plusSeconds(60), "ARS:1→2", "CHE:2→1"), BASE.plusSeconds(60));

        repo.save(row);

        FinalTablePrediction found = repo.findByUserAndSeason(userId, seasonId).orElseThrow();
        assertThat(found.getRankings()).containsExactlyElementsOf(rankings());
        assertThat(found.getSwaps()).hasSize(1);
        assertThat(found.getSwapCount()).isEqualTo(1);
        assertThat(found.getSettledAt()).isEqualTo(BASE.plusSeconds(60));
        assertThat(found.isScored()).isFalse();
        assertThat(found.getCreateDate()).isNotNull();
    }

    @Test
    void saveUpdatesExistingRowRatherThanInserting() {
        UUID userId = insertUser("u1");
        FinalTablePrediction saved = repo.save(newRow(userId, BASE));

        saved.addSwap(new SwapChange(BASE.plusSeconds(60), "ARS:1→3", "LIV:3→1"), BASE.plusSeconds(60));
        repo.save(saved);

        assertThat(repo.countBySeason(seasonId)).isEqualTo(1);
        assertThat(repo.findByUserAndSeason(userId, seasonId).orElseThrow().getSwapCount())
                .isEqualTo(1);
    }

    @Test
    void neverSwappedRowSettlesAtItsCreateDate() {
        // The column is NOT NULL and defaulted at insert, so there is no coalesce in the query:
        // a player who accepts the baseline has settled the moment they entered.
        UUID userId = insertUser("u1");
        FinalTablePrediction row = FinalTablePrediction.builder()
                .userId(userId)
                .seasonId(seasonId)
                .rankings(rankings())
                .build();

        FinalTablePrediction saved = repo.save(row);

        assertThat(saved.getSettledAt()).isNotNull();
        assertThat(saved.getSettledAt()).isEqualTo(saved.getCreateDate().toInstant());
        assertThat(saved.getSwapCount()).isZero();
    }

    @Test
    void leaderboardOrdersByScoreThenZeroesThenEarliestSettled() {
        saveScored("mid", 300, 5, BASE, null);
        saveScored("top", 350, 8, BASE, null);
        saveScored("low", 200, 2, BASE, null);

        List<FinalTableLeaderboardEntry> board = repo.leaderboard(seasonId, 0, 10);

        assertThat(board)
                .extracting(FinalTableLeaderboardEntry::publicId)
                .containsExactly(publicId("top"), publicId("mid"), publicId("low"));
        assertThat(board).extracting(FinalTableLeaderboardEntry::position).containsExactly(1, 2, 3);
    }

    @Test
    void zeroesBreakTiesOnScore() {
        saveScored("fewer", 300, 3, BASE, null);
        saveScored("more", 300, 7, BASE, null);

        assertThat(repo.leaderboard(seasonId, 0, 10))
                .extracting(FinalTableLeaderboardEntry::publicId)
                .containsExactly(publicId("more"), publicId("fewer"));
    }

    @Test
    void earliestSettledBreaksTiesOnScoreAndZeroes() {
        // The distinguishing rule of this game: same score, same zeroes, the player who settled
        // first wins. Swap counts deliberately do not participate.
        saveScored("late", 300, 5, BASE.plusSeconds(7200), null);
        saveScored("early", 300, 5, BASE, null);

        assertThat(repo.leaderboard(seasonId, 0, 10))
                .extracting(FinalTableLeaderboardEntry::publicId)
                .containsExactly(publicId("early"), publicId("late"));
    }

    @Test
    void neverSwappedPlayerIsOrderedByTheirCreateDate() {
        // Their settledAt is the create date, so an early joiner who never swapped still beats a
        // player who settled later.
        saveScored("swlt", 300, 5, BASE.plusSeconds(7200), BASE.plusSeconds(7200));

        UUID userId = insertUser("nvsw");
        FinalTablePrediction row = FinalTablePrediction.builder()
                .userId(userId)
                .seasonId(seasonId)
                .rankings(rankings())
                .build();
        row.setBaseScore(250);
        row.setZeroesCount(5);
        row.setBonusPoints(50);
        row.setTotalScore(300);
        row.setScoredAt(BASE.plusSeconds(999_999));
        FinalTablePrediction saved = repo.save(row);

        // Force both the create date and the settle time to an earlier instant, mirroring how the
        // row would have been inserted months before.
        OffsetDateTime early = OffsetDateTime.ofInstant(BASE, ZoneOffset.UTC);
        dsl.update(T_FINAL_TABLE_PREDICTION)
                .set(T_FINAL_TABLE_PREDICTION.C_CREATE_DATE, early)
                .set(T_FINAL_TABLE_PREDICTION.C_SETTLED_AT, early)
                .where(T_FINAL_TABLE_PREDICTION.PK_ID.eq(saved.getId()))
                .execute();

        assertThat(repo.leaderboard(seasonId, 0, 10))
                .extracting(FinalTableLeaderboardEntry::publicId)
                .containsExactly(publicId("nvsw"), publicId("swlt"));
    }

    @Test
    void swapCountIsReportedButDoesNotOrderTheBoard() {
        UUID busyId = insertUser("busy");
        FinalTablePrediction busy = newRow(busyId, BASE.plusSeconds(7200));
        for (int i = 0; i < 5; i++) {
            busy.addSwap(new SwapChange(BASE, "ARS:1→2", "CHE:2→1"), BASE.plusSeconds(7200));
        }
        busy.setBaseScore(250);
        busy.setZeroesCount(5);
        busy.setBonusPoints(50);
        busy.setTotalScore(300);
        busy.setScoredAt(BASE.plusSeconds(999_999));
        repo.save(busy);

        saveScored("thrifty", 300, 5, BASE, null);

        List<FinalTableLeaderboardEntry> board = repo.leaderboard(seasonId, 0, 10);

        // Ordered on settle time, not swaps — the busy player settled later and so ranks below.
        assertThat(board)
                .extracting(FinalTableLeaderboardEntry::publicId)
                .containsExactly(publicId("thrifty"), publicId("busy"));
        assertThat(board.get(0).swapCount()).isZero();
        assertThat(board.get(1).swapCount()).isEqualTo(5);
    }

    @Test
    void leaderboardExcludesUnscoredRowsAndPaginates() {
        saveScored("a", 350, 8, BASE, null);
        saveScored("b", 300, 5, BASE, null);
        saveScored("c", 250, 3, BASE, null);
        repo.save(newRow(insertUser("unsc"), BASE));

        assertThat(repo.countBySeason(seasonId)).isEqualTo(4);
        assertThat(repo.countScoredBySeason(seasonId)).isEqualTo(3);

        assertThat(repo.leaderboard(seasonId, 0, 2))
                .extracting(FinalTableLeaderboardEntry::publicId)
                .containsExactly(publicId("a"), publicId("b"));
        assertThat(repo.leaderboard(seasonId, 2, 2))
                .extracting(FinalTableLeaderboardEntry::publicId)
                .containsExactly(publicId("c"));
    }

    @Test
    void userStandingCarriesTheSeasonWidePosition() {
        saveScored("first", 350, 8, BASE, null);
        FinalTablePrediction second = saveScored("second", 300, 5, BASE, null);
        saveScored("third", 250, 3, BASE, null);

        FinalTableLeaderboardEntry entry =
                repo.userStanding(seasonId, second.getUserId()).orElseThrow();

        // Not 1: the position must come from the whole season's ranking, not from a filtered query.
        assertThat(entry.position()).isEqualTo(2);
        assertThat(entry.publicId()).isEqualTo(publicId("second"));
        assertThat(entry.totalScore()).isEqualTo(300);
        assertThat(entry.displayName()).isEqualTo("Player second");
    }

    @Test
    void userStandingIsEmptyForUnscoredOrAbsentRows() {
        UUID unscoredId = insertUser("unsc");
        repo.save(newRow(unscoredId, BASE));

        assertThat(repo.userStanding(seasonId, unscoredId)).isEmpty();
        assertThat(repo.userStanding(seasonId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void scoredRowRoundTripsItsResultColumns() {
        UUID userId = insertUser("u1");
        FinalTablePrediction row = newRow(userId, BASE);
        row.setResultRankings(List.of(new ResultTeamRank(TeamRank.of("ARS", 1), 3, 2)));
        row.setBaseScore(196);
        row.setZeroesCount(16);
        row.setBonusPoints(160);
        row.setTotalScore(356);
        row.setScoredAt(BASE.plusSeconds(500));

        repo.save(row);

        FinalTablePrediction found = repo.findByUserAndSeason(userId, seasonId).orElseThrow();
        assertThat(found.isScored()).isTrue();
        assertThat(found.getTotalScore()).isEqualTo(356);
        assertThat(found.getResultRankings()).hasSize(1);
        assertThat(found.getResultRankings().get(0).getHit()).isEqualTo(2);
    }

    @Test
    void clearResultsReturnsRowsToTheWaitingStateWithoutLosingThePrediction() {
        saveScored("a", 350, 8, BASE, null);
        saveScored("b", 300, 5, BASE, null);

        int cleared = repo.clearResults(seasonId);

        assertThat(cleared).isEqualTo(2);
        assertThat(repo.countScoredBySeason(seasonId)).isZero();
        assertThat(repo.leaderboard(seasonId, 0, 10)).isEmpty();

        FinalTablePrediction row = repo.findBySeason(seasonId).get(0);
        // scoredAt must be nulled too — it is the reveal predicate, so leaving it set would show
        // a "revealed" page with blank numbers.
        assertThat(row.isScored()).isFalse();
        assertThat(row.getTotalScore()).isNull();
        assertThat(row.getBaseScore()).isNull();
        assertThat(row.getZeroesCount()).isNull();
        assertThat(row.getBonusPoints()).isNull();
        assertThat(row.getResultRankings()).isNull();
        // The prediction itself survives.
        assertThat(row.getRankings()).containsExactlyElementsOf(rankings());
    }

    @Test
    void findBySeasonAndDeleteByUserAreScopedCorrectly() {
        UUID keptId = insertUser("kept");
        UUID goneId = insertUser("gone");
        repo.save(newRow(keptId, BASE));
        repo.save(newRow(goneId, BASE));

        assertThat(repo.findBySeason(seasonId)).hasSize(2);

        repo.deleteByUserId(goneId);

        assertThat(repo.findBySeason(seasonId)).hasSize(1);
        assertThat(repo.findByUserAndSeason(keptId, seasonId)).isPresent();
        assertThat(repo.findByUserAndSeason(goneId, seasonId)).isEmpty();
    }
}
