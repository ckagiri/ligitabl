package com.ligitabl.api.usecases.leaderboard;

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

        var results = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1);

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

        var results = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 3);

        assertThat(results.get(0).displayName()).isEqualTo("Alice");
        assertThat(results.get(0).totalScore()).isEqualTo(180);
        assertThat(results.get(0).totalZeroes()).isEqualTo(18);
        assertThat(results.get(0).totalSwaps()).isEqualTo(9);

        assertThat(results.get(1).displayName()).isEqualTo("Bob");
        assertThat(results.get(1).totalScore()).isEqualTo(150);
    }

    @Test
    @DisplayName("sorts by score desc, then zeroes desc, swaps asc, max score desc, display name")
    void sortsWithTiebreakers() {
        // Score DESC
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 120, 12, 6);
        createResult(charlieId, charliePredictionId, 1, 90, 9, 4);

        var byScore = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1);
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
        var byZeroes = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1);
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
        var bySwaps = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1);
        assertThat(bySwaps.get(0).displayName()).isEqualTo("Bob");
        assertThat(bySwaps.get(1).displayName()).isEqualTo("Alice");
        assertThat(bySwaps.get(2).displayName()).isEqualTo("Charlie");

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

        var byMax = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 2);
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

        // Display name (everything tied)
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 100, 10, 5);
        createResult(charlieId, charliePredictionId, 1, 100, 10, 5);
        var byName = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1);
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

        var results = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 2);

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

        var results = leaderboardRepo.computeLeaderboard(contestId, seasonId, 5, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).totalScore()).isEqualTo(130);
        assertThat(results.get(0).totalZeroes()).isEqualTo(13);
        assertThat(results.get(0).totalSwaps()).isEqualTo(7);
    }

    @Test
    @DisplayName("returns empty when no results")
    void returnsEmptyWhenNoResults() {
        var results = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("only includes users who entered the contest")
    void onlyIncludesContestEntries() {
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 90, 9, 4);

        UUID daveId = insertUser("dave@example.com", "Dave");
        UUID davePredictionId = createPrediction(daveId);
        createResult(daveId, davePredictionId, 1, 120, 12, 6);

        var results = leaderboardRepo.computeLeaderboard(contestId, seasonId, 1, 1);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(r -> r.displayName()).containsExactly("Alice", "Bob");
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

    private UUID createPrediction(UUID userId) {
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(List.of())
                .currentRankings(List.of())
                .swaps(List.of())
                .atRoundNumber(1)
                .build();

        seasonPredictionRepo.save(prediction);
        return prediction.getId();
    }

    private void createResult(UUID userId, UUID predictionId, int roundPosition, int score, int zeroes, int swaps) {
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
                .score(score)
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
