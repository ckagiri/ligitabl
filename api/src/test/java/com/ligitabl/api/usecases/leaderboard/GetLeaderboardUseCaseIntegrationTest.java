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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.RoundSubmission;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@DisplayName("GetLeaderboardUseCase Integration Tests")
class GetLeaderboardUseCaseIntegrationTest extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GetLeaderboardUseCase useCase;

    @Autowired
    CompetitionDefaults competitionDefaults;

    @Autowired
    EntryRepo entryRepo;

    @Autowired
    SeasonPredictionRepo seasonPredictionRepo;

    @Autowired
    RoundSubmissionRepo roundSubmissionRepo;

    @Autowired
    RoundResultRepo roundResultRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID competitionId;
    private UUID seasonId;
    private UUID contestId;

    private UUID aliceId;
    private UUID bobId;
    private UUID charlieId;

    private UUID alicePredictionId;
    private UUID bobPredictionId;
    private UUID charliePredictionId;

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
    void setup() throws Exception {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();

        insertCompetitionSeasonAndContest(defaultPhasesJson());

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
    @DisplayName("returns full season leaderboard when no phase specified")
    void returnsFullSeasonLeaderboardByDefault() {
        // Alice: round 1 + 38
        createResult(aliceId, alicePredictionId, 1, 40, 4, 1);
        createResult(aliceId, alicePredictionId, 38, 40, 4, 1);

        // Bob: round 1 + 38 (slightly worse)
        createResult(bobId, bobPredictionId, 1, 35, 3, 1);
        createResult(bobId, bobPredictionId, 38, 35, 3, 1);

        var result = useCase.execute(new GetLeaderboardQuery(null));

        assertThat(result.isRight()).isTrue();
        var leaderboard = result.get();

        assertThat(leaderboard.contestId()).isEqualTo(contestId);
        assertThat(leaderboard.phase().getCode()).isEqualTo("FS");
        assertThat(leaderboard.phase().getName()).isEqualTo("Full Season");
        assertThat(leaderboard.phase().getFrom()).isEqualTo(1);
        assertThat(leaderboard.phase().getTo()).isEqualTo(38);

        assertThat(leaderboard.rankings()).hasSize(2);
        assertThat(leaderboard.rankings().get(0).displayName()).isEqualTo("Alice");
        assertThat(leaderboard.rankings().get(0).totalScore()).isEqualTo(80);
        assertThat(leaderboard.rankings().get(1).displayName()).isEqualTo("Bob");
        assertThat(leaderboard.rankings().get(1).totalScore()).isEqualTo(70);
    }

    @Test
    @DisplayName("returns Q1 leaderboard for rounds 1-10")
    void returnsQ1Leaderboard() {
        createResults(aliceId, alicePredictionId, 1, 10, 10);
        createResults(bobId, bobPredictionId, 1, 10, 20);
        createResults(charlieId, charliePredictionId, 1, 10, 5);

        var result = useCase.execute(new GetLeaderboardQuery("Q1"));

        assertThat(result.isRight()).isTrue();
        var leaderboard = result.get();

        assertThat(leaderboard.phase().getCode()).isEqualTo("Q1");
        assertThat(leaderboard.phase().getName()).isEqualTo("Quarter 1");
        assertThat(leaderboard.phase().getFrom()).isEqualTo(1);
        assertThat(leaderboard.phase().getTo()).isEqualTo(10);

        assertThat(leaderboard.rankings()).hasSize(3);
        assertThat(leaderboard.rankings().get(0).displayName()).isEqualTo("Bob");
        assertThat(leaderboard.rankings().get(0).totalScore()).isEqualTo(200);
        assertThat(leaderboard.rankings().get(1).displayName()).isEqualTo("Alice");
        assertThat(leaderboard.rankings().get(1).totalScore()).isEqualTo(100);
        assertThat(leaderboard.rankings().get(2).displayName()).isEqualTo("Charlie");
        assertThat(leaderboard.rankings().get(2).totalScore()).isEqualTo(50);
    }

    @Test
    @DisplayName("returns Q2 leaderboard for rounds 11-20")
    void returnsQ2Leaderboard() {
        createResults(aliceId, alicePredictionId, 11, 20, 50);
        createResults(bobId, bobPredictionId, 11, 20, 40);
        createResults(charlieId, charliePredictionId, 11, 20, 45);

        var result = useCase.execute(new GetLeaderboardQuery("Q2"));

        assertThat(result.isRight()).isTrue();
        var leaderboard = result.get();

        assertThat(leaderboard.phase().getCode()).isEqualTo("Q2");
        assertThat(leaderboard.phase().getName()).isEqualTo("Quarter 2");
        assertThat(leaderboard.phase().getFrom()).isEqualTo(11);
        assertThat(leaderboard.phase().getTo()).isEqualTo(20);

        assertThat(leaderboard.rankings()).hasSize(3);
        assertThat(leaderboard.rankings().get(0).displayName()).isEqualTo("Alice");
        assertThat(leaderboard.rankings().get(0).totalScore()).isEqualTo(500);
    }

    @Test
    @DisplayName("ranks by total zeroes when scores are tied")
    void ranksByTotalZeroesWhenScoresTied() {
        // Same score, different zeroes
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 100, 12, 5);

        var result = useCase.execute(new GetLeaderboardQuery(null));

        assertThat(result.isRight()).isTrue();
        var rankings = result.get().rankings();

        assertThat(rankings).hasSize(2);
        assertThat(rankings.get(0).displayName()).isEqualTo("Bob");
        assertThat(rankings.get(0).totalZeroes()).isEqualTo(12);
        assertThat(rankings.get(1).displayName()).isEqualTo("Alice");
        assertThat(rankings.get(1).totalZeroes()).isEqualTo(10);
    }

    @Test
    @DisplayName("ranks by total swaps when scores and zeroes are tied")
    void ranksByTotalSwapsWhenScoreAndZeroesTied() {
        // Same score and zeroes, different swaps (fewer swaps is better)
        createResult(aliceId, alicePredictionId, 1, 100, 10, 5);
        createResult(bobId, bobPredictionId, 1, 100, 10, 3);

        var result = useCase.execute(new GetLeaderboardQuery(null));

        assertThat(result.isRight()).isTrue();
        var rankings = result.get().rankings();

        assertThat(rankings).hasSize(2);
        assertThat(rankings.get(0).displayName()).isEqualTo("Bob");
        assertThat(rankings.get(0).totalSwaps()).isEqualTo(3);
        assertThat(rankings.get(1).displayName()).isEqualTo("Alice");
        assertThat(rankings.get(1).totalSwaps()).isEqualTo(5);
    }

    @Test
    @DisplayName("ranks by max score when all other stats are tied")
    void ranksByMaxScoreWhenAllOtherStatsTied() {
        // Totals tied (100), zeroes tied (0), swaps tied (0) but max score differs.
        // Alice scores 50 + 50 (max 50)
        createResult(aliceId, alicePredictionId, 1, 50, 0, 0);
        createResult(aliceId, alicePredictionId, 2, 50, 0, 0);

        // Bob scores 0 + 100 (max 100)
        createResult(bobId, bobPredictionId, 1, 0, 0, 0);
        createResult(bobId, bobPredictionId, 2, 100, 0, 0);

        var result = useCase.execute(new GetLeaderboardQuery(null));

        assertThat(result.isRight()).isTrue();
        var rankings = result.get().rankings();

        assertThat(rankings).hasSize(2);
        assertThat(rankings.get(0).displayName()).isEqualTo("Bob");
        assertThat(rankings.get(0).maxScore()).isEqualTo(100);
        assertThat(rankings.get(1).displayName()).isEqualTo("Alice");
        assertThat(rankings.get(1).maxScore()).isEqualTo(50);
    }

    @Test
    @DisplayName("calculates position movement correctly")
    void calculatesPositionMovement() {
        // Round 1 standings: Alice (1st), Bob (2nd), Charlie (3rd)
        createResult(aliceId, alicePredictionId, 1, 50, 0, 0);
        createResult(bobId, bobPredictionId, 1, 40, 0, 0);
        createResult(charlieId, charliePredictionId, 1, 30, 0, 0);

        // Round 2 totals:
        // Bob: 40 + 60 = 100 (moves up 1)
        // Alice: 50 + 30 = 80 (moves down 1)
        // Charlie: 30 + 25 = 55 (no movement)
        createResult(aliceId, alicePredictionId, 2, 30, 0, 0);
        createResult(bobId, bobPredictionId, 2, 60, 0, 0);
        createResult(charlieId, charliePredictionId, 2, 25, 0, 0);

        var result = useCase.execute(new GetLeaderboardQuery(null));

        assertThat(result.isRight()).isTrue();
        var rankings = result.get().rankings();

        assertThat(rankings).hasSize(3);

        assertThat(rankings.get(0).displayName()).isEqualTo("Bob");
        assertThat(rankings.get(0).position()).isEqualTo(1);
        assertThat(rankings.get(0).movement()).isEqualTo(1);

        assertThat(rankings.get(1).displayName()).isEqualTo("Alice");
        assertThat(rankings.get(1).position()).isEqualTo(2);
        assertThat(rankings.get(1).movement()).isEqualTo(-1);

        assertThat(rankings.get(2).displayName()).isEqualTo("Charlie");
        assertThat(rankings.get(2).position()).isEqualTo(3);
        assertThat(rankings.get(2).movement()).isEqualTo(0);
    }

    @Test
    @DisplayName("returns empty leaderboard when no submissions")
    void returnsEmptyLeaderboardWhenNoSubmissions() {
        var result = useCase.execute(new GetLeaderboardQuery(null));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().rankings()).isEmpty();
    }

    @Test
    @DisplayName("calculates movement within the requested phase")
    void calculatesMovementWithinPhase() {
        // Build up Q1 such that after round 9 Alice leads, but after adding round 10 Bob overtakes.
        createResults(aliceId, alicePredictionId, 1, 9, 10); // 90
        createResults(bobId, bobPredictionId, 1, 9, 9); // 81

        createResult(aliceId, alicePredictionId, 10, 0, 0, 0);
        createResult(bobId, bobPredictionId, 10, 20, 0, 0);

        var result = useCase.execute(new GetLeaderboardQuery("Q1"));

        assertThat(result.isRight()).isTrue();
        var leaderboard = result.get();

        assertThat(leaderboard.rankings()).hasSize(2);
        assertThat(leaderboard.rankings().get(0).displayName()).isEqualTo("Bob");
        assertThat(leaderboard.rankings().get(0).movement()).isEqualTo(1);

        assertThat(leaderboard.rankings().get(1).displayName()).isEqualTo("Alice");
        assertThat(leaderboard.rankings().get(1).movement()).isEqualTo(-1);
    }

    @Test
    @DisplayName("returns InvalidPhase when phase code is unknown")
    void returnsInvalidPhase() {
        var result = useCase.execute(new GetLeaderboardQuery("Q9"));
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(GetLeaderboardError.InvalidPhase.class);
    }

    @Test
    @DisplayName("returns PhasesNotConfigured when competition has no phases")
    void returnsPhasesNotConfigured() throws Exception {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();

        insertCompetitionSeasonAndContest("[]");

        var result = useCase.execute(new GetLeaderboardQuery(null));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(GetLeaderboardError.PhasesNotConfigured.class);
    }

    @Test
    @DisplayName("returns DefaultCompetitionNotFound when the default competition does not exist")
    void returnsDefaultCompetitionNotFound() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        var result = useCase.execute(new GetLeaderboardQuery(null));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(GetLeaderboardError.DefaultCompetitionNotFound.class);
    }

    private String defaultPhasesJson() {
        try {
            var phases = List.of(
                    RoundSpan.builder()
                            .code("FS")
                            .name("Full Season")
                            .from(1)
                            .to(38)
                            .build(),
                    RoundSpan.builder()
                            .code("Q1")
                            .name("Quarter 1")
                            .from(1)
                            .to(10)
                            .build(),
                    RoundSpan.builder()
                            .code("Q2")
                            .name("Quarter 2")
                            .from(11)
                            .to(20)
                            .build(),
                    RoundSpan.builder()
                            .code("Q3")
                            .name("Quarter 3")
                            .from(21)
                            .to(30)
                            .build(),
                    RoundSpan.builder()
                            .code("Q4")
                            .name("Quarter 4")
                            .from(31)
                            .to(38)
                            .build());
            return objectMapper.writeValueAsString(phases);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize phases", e);
        }
    }

    private void insertCompetitionSeasonAndContest(String phasesJson) {
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?, ?::jsonb, ?)",
                competitionId,
                "Premier League",
                competitionDefaults.defaultCompetitionSlug(),
                "PL",
                phasesJson,
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

    private void createResults(UUID userId, UUID predictionId, int fromRound, int toRound, int scorePerRound) {
        for (int roundPosition = fromRound; roundPosition <= toRound; roundPosition++) {
            createResult(userId, predictionId, roundPosition, scorePerRound, 0, 0);
        }
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
}
