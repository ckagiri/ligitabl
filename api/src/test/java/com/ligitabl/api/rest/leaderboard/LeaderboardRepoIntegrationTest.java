package com.ligitabl.api.rest.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
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
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSubmission;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@DisplayName("LeaderboardRepo Integration Tests")
class LeaderboardRepoIntegrationTest extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LeaderboardRepo leaderboardRepo;

    @Autowired
    EntryRepo entryRepo;

    @Autowired
    SeasonPredictionRepo seasonPredictionRepo;

    @Autowired
    RoundSubmissionRepo roundSubmissionRepo;

    @Autowired
    RoundResultRepo roundResultRepo;

    private UUID competitionId;
    private UUID seasonId;
    private UUID contestId;

    private UUID aliceId;
    private UUID bobId;
    private UUID charlieId;

    private UUID alicePredictionId;
    private UUID bobPredictionId;
    private UUID charliePredictionId;

    private List<LeaderboardEntry> computeEntries(int fromRound, int toRound) {
        return leaderboardRepo
                .computeLeaderboard(contestId, seasonId, fromRound, toRound, null, 0, 100)
                .entries();
    }

    private void ensureFinalizedRound(int roundPosition) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_round WHERE fk_season_id = ? AND c_position = ?",
                Integer.class,
                seasonId,
                roundPosition);

        if (count != null && count > 0) {
            jdbc.update(
                    "UPDATE t_round SET c_is_finalized = true WHERE fk_season_id = ? AND c_position = ?",
                    seasonId,
                    roundPosition);
            return;
        }

        jdbc.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                seasonId,
                "Round " + roundPosition,
                "round-" + roundPosition,
                roundPosition,
                true);
    }

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();

        insertCompetitionSeasonAndContest();

        aliceId = insertUser("alice@example.com", "Alice");
        bobId = insertUser("bob@example.com", "Bob");
        charlieId = insertUser("charlie@example.com", "Charlie");

        alicePredictionId = createPrediction(aliceId);
        bobPredictionId = createPrediction(bobId);
        charliePredictionId = createPrediction(charlieId);

        entryRepo.save(Entry.builder().userId(aliceId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(bobId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(charlieId).contestId(contestId).build());
    }

    @Test
    @DisplayName("computes basic leaderboard with aggregation")
    void computesBasicLeaderboard() {
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 90, 9, 4);
        createResult(charlieId, charliePredictionId, 1, 80, 8, 3);

        var results = computeEntries(1, 1);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).displayName()).isEqualTo("Alice");
        assertThat(results.get(0).position()).isEqualTo(1);
        assertThat(results.get(0).totalScore()).isEqualTo(100);
        assertThat(results.get(1).displayName()).isEqualTo("Bob");
        assertThat(results.get(2).displayName()).isEqualTo("Charlie");
    }

    @Test
    @DisplayName("aggregates across multiple rounds")
    void aggregatesAcrossMultipleRounds() {
        createResult(aliceId, alicePredictionId, 1, 50, 5, 2);
        createResult(aliceId, alicePredictionId, 2, 60, 6, 3);
        createResult(aliceId, alicePredictionId, 3, 70, 7, 4);

        createResult(bobId, bobPredictionId, 1, 40, 4, 1);
        createResult(bobId, bobPredictionId, 2, 50, 5, 2);
        createResult(bobId, bobPredictionId, 3, 60, 6, 3);

        var results = computeEntries(1, 3);

        assertThat(results.get(0).displayName()).isEqualTo("Alice");
        assertThat(results.get(0).totalScore()).isEqualTo(180);
        assertThat(results.get(0).totalZeroes()).isEqualTo(18);
        assertThat(results.get(0).totalSwaps()).isEqualTo(9);

        assertThat(results.get(1).displayName()).isEqualTo("Bob");
        assertThat(results.get(1).totalScore()).isEqualTo(150);
    }

    @Test
    @DisplayName("fetches only the requested page")
    void fetchesOnlyRequestedPage() {
        resetData();

        var users = createParticipantsWithScores(40, 1, 1000);

        var response = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1, users.get(0), 20, 20);

        assertThat(response.entries()).hasSize(20);
        assertThat(response.entries().get(0).position()).isEqualTo(21);
        assertThat(response.entries().get(19).position()).isEqualTo(40);
        assertThat(response.totalParticipants()).isEqualTo(40);
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("returns user position even when not in current page")
    void returnsUserPositionOutsideCurrentPage() {
        resetData();

        var users = createParticipantsWithScores(40, 1, 1000);
        UUID midRankedUser = users.get(29);

        var response = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1, midRankedUser, 0, 20);

        assertThat(response.userEntry()).isNotNull();
        assertThat(response.userEntry().position()).isEqualTo(30);
        assertThat(response.userInCurrentPage()).isFalse();
        assertThat(response.userPageOffset()).isEqualTo(20);
        assertThat(response.entries()).hasSize(20);
        assertThat(response.entries().get(0).position()).isEqualTo(1);
    }

    @Test
    @DisplayName("marks user when in current page")
    void marksUserInCurrentPage() {
        resetData();

        var users = createParticipantsWithScores(40, 1, 1000);
        UUID topRankedUser = users.get(14);

        var response = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1, topRankedUser, 0, 20);

        assertThat(response.userEntry()).isNotNull();
        assertThat(response.userEntry().position()).isEqualTo(15);
        assertThat(response.userInCurrentPage()).isTrue();
        assertThat(response.entries()).anyMatch(entry -> entry.position() == 15);
    }

    @Test
    @DisplayName("calculates max score correctly")
    void calculatesMaxScoreCorrectly() {
        createResult(aliceId, alicePredictionId, 1, 10, 1, 1);
        createResult(aliceId, alicePredictionId, 2, 90, 9, 2);

        var results = computeEntries(1, 2);

        // Alice is scored; Bob and Charlie have predictions but no results
        assertThat(results).hasSize(3);
        assertThat(results.get(0).displayName()).isEqualTo("Alice");
        assertThat(results.get(0).scored()).isTrue();
        assertThat(results.get(0).totalScore()).isEqualTo(100);
        assertThat(results.get(0).maxScore()).isEqualTo(90);
        assertThat(results.get(1).scored()).isFalse();
        assertThat(results.get(2).scored()).isFalse();
    }

    @Test
    @DisplayName("sorts by score desc, then zeroes desc, max score desc, swaps asc, public id")
    void sortsWithTiebreakers() {
        // Score DESC
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 120, 12, 6);
        createResult(charlieId, charliePredictionId, 1, 90, 9, 4);

        var byScore = computeEntries(1, 1);
        assertThat(byScore.get(0).displayName()).isEqualTo("Bob");
        assertThat(byScore.get(1).displayName()).isEqualTo("Alice");
        assertThat(byScore.get(2).displayName()).isEqualTo("Charlie");

        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        insertCompetitionSeasonAndContest();
        aliceId = insertUser("alice@example.com", "Alice");
        bobId = insertUser("bob@example.com", "Bob");
        charlieId = insertUser("charlie@example.com", "Charlie");
        alicePredictionId = createPrediction(aliceId);
        bobPredictionId = createPrediction(bobId);
        charliePredictionId = createPrediction(charlieId);
        entryRepo.save(Entry.builder().userId(aliceId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(bobId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(charlieId).contestId(contestId).build());

        // Zeroes DESC (score tied)
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 100, 12, 5);
        createResult(charlieId, charliePredictionId, 1, 100, 8, 5);
        var byZeroes = computeEntries(1, 1);
        assertThat(byZeroes.get(0).displayName()).isEqualTo("Bob");
        assertThat(byZeroes.get(1).displayName()).isEqualTo("Alice");
        assertThat(byZeroes.get(2).displayName()).isEqualTo("Charlie");

        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        insertCompetitionSeasonAndContest();
        aliceId = insertUser("alice@example.com", "Alice");
        bobId = insertUser("bob@example.com", "Bob");
        charlieId = insertUser("charlie@example.com", "Charlie");
        alicePredictionId = createPrediction(aliceId);
        bobPredictionId = createPrediction(bobId);
        charliePredictionId = createPrediction(charlieId);
        entryRepo.save(Entry.builder().userId(aliceId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(bobId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(charlieId).contestId(contestId).build());

        // Swaps ASC (score+zeroes tied)
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 100, 10, 3);
        createResult(charlieId, charliePredictionId, 1, 100, 10, 7);
        var bySwaps = computeEntries(1, 1);
        assertThat(bySwaps.get(0).displayName()).isEqualTo("Bob");
        assertThat(bySwaps.get(1).displayName()).isEqualTo("Alice");
        assertThat(bySwaps.get(2).displayName()).isEqualTo("Charlie");

        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        insertCompetitionSeasonAndContest();
        aliceId = insertUser("alice@example.com", "Alice");
        bobId = insertUser("bob@example.com", "Bob");
        alicePredictionId = createPrediction(aliceId);
        bobPredictionId = createPrediction(bobId);
        entryRepo.save(Entry.builder().userId(aliceId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(bobId).contestId(contestId).build());

        // Max score DESC (score+zeroes tied) should beat swaps ASC
        // - Alice: higher max score but worse swaps
        // - Bob: lower max score but better swaps
        // Expected ordering: Alice first (max score precedes swaps)
        createResult(aliceId, alicePredictionId, 1, 100, 0, 10);
        createResult(aliceId, alicePredictionId, 2, 0, 0, 0);
        createResult(bobId, bobPredictionId, 1, 60, 0, 0);
        createResult(bobId, bobPredictionId, 2, 40, 0, 0);

        var byMaxThenSwaps = computeEntries(1, 2);
        assertThat(byMaxThenSwaps).hasSize(2);
        assertThat(byMaxThenSwaps.get(0).displayName()).isEqualTo("Alice");
        assertThat(byMaxThenSwaps.get(1).displayName()).isEqualTo("Bob");

        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        insertCompetitionSeasonAndContest();
        aliceId = insertUser("alice@example.com", "Alice");
        bobId = insertUser("bob@example.com", "Bob");
        charlieId = insertUser("charlie@example.com", "Charlie");
        alicePredictionId = createPrediction(aliceId);
        bobPredictionId = createPrediction(bobId);
        charliePredictionId = createPrediction(charlieId);
        entryRepo.save(Entry.builder().userId(aliceId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(bobId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(charlieId).contestId(contestId).build());

        // Max score DESC (score+zeroes+swaps tied)
        createResult(aliceId, alicePredictionId, 1, 50, 5, 2);
        createResult(aliceId, alicePredictionId, 2, 50, 5, 3); // max 50

        createResult(bobId, bobPredictionId, 1, 10, 1, 1);
        createResult(bobId, bobPredictionId, 2, 90, 9, 4); // max 90

        createResult(charlieId, charliePredictionId, 1, 50, 5, 2);
        createResult(charlieId, charliePredictionId, 2, 50, 5, 3); // max 50

        var byMax = computeEntries(1, 2);
        assertThat(byMax.get(0).displayName()).isEqualTo("Bob");

        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        insertCompetitionSeasonAndContest();
        aliceId = insertUser("alice@example.com", "Alice");
        bobId = insertUser("bob@example.com", "Bob");
        charlieId = insertUser("charlie@example.com", "Charlie");
        alicePredictionId = createPrediction(aliceId);
        bobPredictionId = createPrediction(bobId);
        charliePredictionId = createPrediction(charlieId);
        entryRepo.save(Entry.builder().userId(aliceId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(bobId).contestId(contestId).build());
        entryRepo.save(Entry.builder().userId(charlieId).contestId(contestId).build());

        // Public id (everything tied)
        updateUserPublicId(aliceId, "alice");
        updateUserPublicId(bobId, "bob");
        updateUserPublicId(charlieId, "charlie");
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 100, 10, 5);
        createResult(charlieId, charliePredictionId, 1, 100, 10, 5);
        var byName = computeEntries(1, 1);
        assertThat(byName.get(0).displayName()).isEqualTo("Alice");
        assertThat(byName.get(1).displayName()).isEqualTo("Bob");
        assertThat(byName.get(2).displayName()).isEqualTo("Charlie");
    }

    @Test
    @DisplayName("calculates movement relative to previous round")
    void calculatesMovement() {
        // Round 1
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 90, 9, 4);
        createResult(charlieId, charliePredictionId, 1, 80, 8, 3);

        // Round 2
        createResult(aliceId, alicePredictionId, 2, 50, 5, 2);
        createResult(bobId, bobPredictionId, 2, 120, 12, 6);
        createResult(charlieId, charliePredictionId, 2, 60, 6, 3);

        var results = computeEntries(1, 2);

        assertThat(results.get(0).displayName()).isEqualTo("Bob");
        assertThat(results.get(0).movement()).isEqualTo(1);

        assertThat(results.get(1).displayName()).isEqualTo("Alice");
        assertThat(results.get(1).movement()).isEqualTo(-1);

        assertThat(results.get(2).displayName()).isEqualTo("Charlie");
        assertThat(results.get(2).movement()).isEqualTo(0);
    }

    @Test
    @DisplayName("filters by round range")
    void filtersByRoundRange() {
        createResult(aliceId, alicePredictionId, 1, 50, 5, 2);
        createResult(aliceId, alicePredictionId, 5, 60, 6, 3);
        createResult(aliceId, alicePredictionId, 10, 70, 7, 4);
        createResult(aliceId, alicePredictionId, 15, 80, 8, 5);

        var results = computeEntries(5, 10);

        // Alice is scored with results in range; Bob and Charlie have predictions but no results in range
        assertThat(results).hasSize(3);
        assertThat(results.get(0).displayName()).isEqualTo("Alice");
        assertThat(results.get(0).scored()).isTrue();
        assertThat(results.get(0).totalScore()).isEqualTo(130);
        assertThat(results.get(0).totalZeroes()).isEqualTo(13);
        assertThat(results.get(0).totalSwaps()).isEqualTo(7);
        assertThat(results.get(1).scored()).isFalse();
        assertThat(results.get(2).scored()).isFalse();
    }

    @Test
    @DisplayName("returns participants as unscored when no rounds are finalized")
    void returnsUnscoredParticipantsWhenNoRoundsFinalized() {
        // Alice, Bob, Charlie have predictions and entries but no round results yet
        var results = computeEntries(1, 1);

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(e -> !e.scored());
        assertThat(results).allMatch(e -> e.totalScore() == 0);
    }

    @Test
    @DisplayName("only includes users who entered the contest")
    void onlyIncludesContestEntries() {
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 90, 9, 4);

        // Dave has a prediction and scores but no contest entry — must be excluded
        UUID daveId = insertUser("dave@example.com", "Dave");
        UUID davePredictionId = createPrediction(daveId);
        createResult(daveId, davePredictionId, 1, 120, 12, 6);

        var results = computeEntries(1, 1);

        // Alice and Bob scored; Charlie has an entry+prediction but no results → unscored
        assertThat(results).hasSize(3);
        assertThat(results.get(0).displayName()).isEqualTo("Alice");
        assertThat(results.get(0).scored()).isTrue();
        assertThat(results.get(1).displayName()).isEqualTo("Bob");
        assertThat(results.get(1).scored()).isTrue();
        assertThat(results.get(2).displayName()).isEqualTo("Charlie");
        assertThat(results.get(2).scored()).isFalse();
        assertThat(results).noneMatch(e -> e.displayName().equals("Dave"));
    }

    @Test
    @DisplayName("scored users rank above unscored users")
    void scoredUsersRankAboveUnscoredUsers() {
        // Only Charlie has a result; Alice and Bob have predictions but no scores
        createResult(charlieId, charliePredictionId, 1, 50, 5, 2);

        var results = computeEntries(1, 1);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).displayName()).isEqualTo("Charlie");
        assertThat(results.get(0).scored()).isTrue();
        assertThat(results.get(1).scored()).isFalse();
        assertThat(results.get(2).scored()).isFalse();
    }

    @Test
    @DisplayName("late joiner is excluded from a phase that closed before they joined")
    void lateJoinerExcludedFromPhaseTheyMissed() {
        // Dave joins at round 15; Q1 covers rounds 1-10 — Dave should not appear
        UUID daveId = insertUser("dave@example.com", "Dave");
        createPredictionAtRound(daveId, 15);
        entryRepo.save(Entry.builder().userId(daveId).contestId(contestId).build());

        var results = computeEntries(1, 10);

        assertThat(results).noneMatch(e -> e.displayName().equals("Dave"));
        // Alice, Bob, Charlie (atRoundNumber=1 <= 10) still appear
        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("late joiner is included in a phase that was still open when they joined")
    void lateJoinerIncludedInPhaseTheyJoinedBefore() {
        // Dave joins at round 15; H1 covers rounds 1-19 — Dave should appear (unscored)
        UUID daveId = insertUser("dave@example.com", "Dave");
        createPredictionAtRound(daveId, 15);
        entryRepo.save(Entry.builder().userId(daveId).contestId(contestId).build());

        var results = computeEntries(1, 19);

        assertThat(results).hasSize(4);
        assertThat(results).anyMatch(e -> e.displayName().equals("Dave") && !e.scored());
    }

    private void insertCompetitionSeasonAndContest() {
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?, '[]'::jsonb, ?)",
                competitionId,
                "Premier League",
                "premier-league",
                "PL",
                seasonId);

        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed, fk_current_round_id, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
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

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                id,
                email,
                "test-password-hash",
                displayName,
                randomPublicId(),
                true);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
        return id;
    }

    private void updateUserPublicId(UUID userId, String publicId) {
        jdbc.update("UPDATE t_user SET c_public_id = ? WHERE pk_id = ?", publicId, userId);
    }

    private void resetData() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        insertCompetitionSeasonAndContest();
    }

    private List<UUID> createParticipantsWithScores(int count, int roundPosition, int baseScore) {
        List<UUID> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            UUID userId = insertUser("user" + i + "@example.com", "User " + i);
            UUID predictionId = createPrediction(userId);
            entryRepo.save(Entry.builder().userId(userId).contestId(contestId).build());
            createResult(userId, predictionId, roundPosition, baseScore - i, 0, 0);
            users.add(userId);
        }
        return users;
    }

    private UUID createPrediction(UUID userId) {
        return createPredictionAtRound(userId, 1);
    }

    private UUID createPredictionAtRound(UUID userId, int atRound) {
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(List.of())
                .currentRankings(List.of())
                .swaps(List.of())
                .atRoundNumber(atRound)
                .build();

        seasonPredictionRepo.save(prediction);
        return prediction.getId();
    }

    private void createResult(UUID userId, UUID predictionId, int roundPosition, int score, int zeroes, int swaps) {
        ensureFinalizedRound(roundPosition);
        RoundSubmission submission = RoundSubmission.builder()
                .userId(userId)
                .seasonId(seasonId)
                .roundPosition(roundPosition)
                .rankings(List.<TeamRank>of())
                .seasonPredictionId(predictionId)
                .build();

        RoundSubmission savedSubmission = roundSubmissionRepo.save(submission);

        RoundResult result = RoundResult.builder()
                .id(UUID.randomUUID())
                .roundSubmissionId(savedSubmission.getId())
                .rankings(List.<ResultTeamRank>of())
                .totalScore(score)
                .zeroesCount(zeroes)
                .swapCount(swaps)
                .userViewed(false)
                .build();

        roundResultRepo.save(result);
    }

    private static String randomPublicId() {
        String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(alphabet.length());
            sb.append(alphabet.charAt(idx));
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static List<TeamRank> dummyRankings() {
        return List.of(TeamRank.of("AAA", 1), TeamRank.of("BBB", 2));
    }
}
