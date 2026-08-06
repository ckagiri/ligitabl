package com.ligitabl.api.rest.contest;

import static com.ligitabl.api.testsupport.TestIds.randomPublicId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSubmission;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@DisplayName("EntryPersistenceAdapter Integration Tests")
class EntryPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntryRepo entryRepo;

    @Autowired
    SeasonPredictionRepo predictionRepo;

    @Autowired
    RoundSubmissionRepo roundSubmissionRepo;

    @Autowired
    RoundResultRepo roundResultRepo;

    private UUID seasonId;
    private UUID contestId;
    private UUID userId;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        userId = UUID.randomUUID();

        insertCompetitionSeasonContest();
        insertUser(userId, "user@test.com", "TestUser");
        insertPrediction(userId, 1);
    }

    // ─── softRemove ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("softRemove sets c_removed_at_round; row still present")
    void softRemove_setsRemovedAtRound_rowPresent() {
        entryRepo.save(entry(userId, contestId, 1));

        entryRepo.softRemove(userId, contestId, 5);

        var found = entryRepo.findByUserAndContest(userId, contestId);
        assertThat(found).isPresent();
        assertThat(found.get().getRemovedAtRound()).isEqualTo(5);
    }

    // ─── countActiveByContestId ────────────────────────────────────────────────

    @Test
    @DisplayName("countActiveByContestId excludes soft-removed entries")
    void countActive_excludesSoftRemovedEntries() {
        UUID user2 = UUID.randomUUID();
        insertUser(user2, "user2@test.com", "User2");
        insertPrediction(user2, 1);

        entryRepo.save(entry(userId, contestId, 1));
        entryRepo.save(entry(user2, contestId, 1));

        assertThat(entryRepo.countActiveByContestId(contestId)).isEqualTo(2);

        entryRepo.softRemove(userId, contestId, 3);

        assertThat(entryRepo.countActiveByContestId(contestId)).isEqualTo(1);
    }

    // ─── countActiveByContestIds ───────────────────────────────────────────────

    @Test
    @DisplayName("countActiveByContestIds returns per-contest active counts in one call, omitting contests with none")
    void countActiveByContestIds_returnsCountsPerContest() {
        UUID otherContestId = UUID.randomUUID();
        insertContest(otherContestId);
        UUID emptyContestId = UUID.randomUUID();
        insertContest(emptyContestId);

        UUID user2 = UUID.randomUUID();
        insertUser(user2, "user2@test.com", "User2");
        insertPrediction(user2, 1);

        entryRepo.save(entry(userId, contestId, 1));
        entryRepo.save(entry(user2, contestId, 1));
        entryRepo.save(entry(userId, otherContestId, 1));
        entryRepo.softRemove(userId, otherContestId, 3);

        var counts = entryRepo.countActiveByContestIds(List.of(contestId, otherContestId, emptyContestId));

        assertThat(counts).containsEntry(contestId, 2);
        assertThat(counts).doesNotContainKey(otherContestId);
        assertThat(counts).doesNotContainKey(emptyContestId);
    }

    @Test
    @DisplayName("countActiveByContestIds returns empty map for empty input")
    void countActiveByContestIds_emptyInput() {
        assertThat(entryRepo.countActiveByContestIds(List.of())).isEmpty();
    }

    // ─── hasAnyScore ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasAnyScore returns false when no t_round_submission rows exist in contest window")
    void hasAnyScore_falseWhenNoSubmissions() {
        entryRepo.save(entry(userId, contestId, 1));

        assertThat(entryRepo.hasAnyScore(userId, contestId)).isFalse();
    }

    @Test
    @DisplayName("hasAnyScore returns true after a round within the contest window is submitted")
    void hasAnyScore_trueAfterSubmissionInWindow() {
        entryRepo.save(entry(userId, contestId, 1));

        UUID predId = insertPrediction(userId, 1);
        createRoundSubmissionWithResult(userId, predId, 5);

        assertThat(entryRepo.hasAnyScore(userId, contestId)).isTrue();
    }

    @Test
    @DisplayName("hasAnyScore returns false for submission outside contest window")
    void hasAnyScore_falseWhenSubmissionOutsideContestWindow() {
        // Contest spans rounds 1–10. Submission at round 11 is outside the window.
        UUID contestNarrow = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestNarrow,
                seasonId,
                "Narrow",
                false,
                null,
                1,
                10,
                null);

        entryRepo.save(entry(userId, contestNarrow, 1));

        UUID predId = getPredictionId(userId);
        createRoundSubmissionWithResult(userId, predId, 11); // outside [1, 10]

        assertThat(entryRepo.hasAnyScore(userId, contestNarrow)).isFalse();
    }

    // ─── save (upsert / re-join) ───────────────────────────────────────────────

    @Test
    @DisplayName("save upsert on conflict clears removedAtRound and resets joinedAtRound")
    void save_upsertClearsRemovedAtRoundOnReJoin() {
        entryRepo.save(entry(userId, contestId, 1));
        entryRepo.softRemove(userId, contestId, 3);

        // Re-join: same user+contest, new joinedAtRound
        entryRepo.save(entry(userId, contestId, 5));

        var found = entryRepo.findByUserAndContest(userId, contestId);
        assertThat(found).isPresent();
        assertThat(found.get().getRemovedAtRound()).isNull();
        assertThat(found.get().getJoinedAtRound()).isEqualTo(5);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void insertCompetitionSeasonContest() {
        UUID competitionId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?,'[]'::jsonb,?)",
                competitionId,
                "PL",
                "premier-league",
                "PL",
                seasonId);

        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed, fk_current_round_id, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2025/26",
                "2025-26",
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2026, 5, 31),
                38,
                12,
                "[]",
                false,
                null,
                1);

        jdbc.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestId,
                seasonId,
                "Main League",
                false,
                null,
                1,
                38,
                null);

        jdbc.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);
    }

    private void insertContest(UUID id) {
        jdbc.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                id,
                seasonId,
                "Private League " + id,
                true,
                id.toString().substring(0, 8),
                1,
                38,
                null);
    }

    private void insertUser(UUID id, String email, String displayName) {
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                id,
                email,
                "hash",
                displayName,
                randomPublicId(),
                true);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?,?)", id, "PLAYER");
    }

    private UUID insertPrediction(UUID userId, int atRound) {
        // Return existing prediction id if already present
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_season_prediction WHERE fk_user_id = ? AND fk_season_id = ?",
                Integer.class,
                userId,
                seasonId);
        if (count != null && count > 0) {
            return getPredictionId(userId);
        }
        UUID id = UUID.randomUUID();
        SeasonPrediction pred = SeasonPrediction.builder()
                .id(id)
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(List.of())
                .currentRankings(List.of())
                .swaps(List.of())
                .atRoundNumber(atRound)
                .build();
        predictionRepo.save(pred);
        return id;
    }

    private UUID getPredictionId(UUID userId) {
        return jdbc.queryForObject(
                "SELECT pk_id FROM t_season_prediction WHERE fk_user_id = ? AND fk_season_id = ?",
                UUID.class,
                userId,
                seasonId);
    }

    private void createRoundSubmissionWithResult(UUID userId, UUID predId, int roundPosition) {
        ensureRound(roundPosition);

        RoundSubmission sub = RoundSubmission.builder()
                .userId(userId)
                .seasonId(seasonId)
                .roundPosition(roundPosition)
                .rankings(List.<TeamRank>of())
                .seasonPredictionId(predId)
                .build();
        RoundSubmission saved = roundSubmissionRepo.save(sub);

        RoundResult result = RoundResult.builder()
                .id(UUID.randomUUID())
                .roundSubmissionId(saved.getId())
                .rankings(List.<ResultTeamRank>of())
                .totalScore(50)
                .zeroesCount(0)
                .swapCount(0)
                .userViewed(false)
                .build();
        roundResultRepo.save(result);
    }

    private void ensureRound(int pos) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_round WHERE fk_season_id = ? AND c_position = ?", Integer.class, seasonId, pos);
        if (count == null || count == 0) {
            jdbc.update(
                    "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized, c_advanced) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    seasonId,
                    "Round " + pos,
                    "round-" + pos,
                    pos,
                    true,
                    true);
        } else {
            jdbc.update(
                    "UPDATE t_round SET c_is_finalized = true, c_advanced = true WHERE fk_season_id = ? AND c_position = ?",
                    seasonId,
                    pos);
        }
    }

    private static Entry entry(UUID userId, UUID contestId, int joinedAtRound) {
        return Entry.builder()
                .userId(userId)
                .contestId(contestId)
                .joinedAtRound(joinedAtRound)
                .build();
    }
}
