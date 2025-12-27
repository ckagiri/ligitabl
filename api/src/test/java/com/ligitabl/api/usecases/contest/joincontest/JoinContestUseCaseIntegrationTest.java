package com.ligitabl.api.usecases.contest.joincontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@DisplayName("JoinContestUseCase Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoinContestUseCaseIntegrationTest extends AbstractPostgresIT {

    private static final String SEASON_SLUG = "2024-25";

    private static final List<TeamRank> INITIAL_RANKINGS = List.of(
            new TeamRank("MCI", 1),
            new TeamRank("ARS", 2),
            new TeamRank("LIV", 3),
            new TeamRank("AVL", 4),
            new TeamRank("CHE", 5),
            new TeamRank("NEW", 6),
            new TeamRank("MUN", 7),
            new TeamRank("TOT", 8),
            new TeamRank("BHA", 9),
            new TeamRank("CRY", 10),
            new TeamRank("BRE", 11),
            new TeamRank("WHU", 12));

    @Autowired
    JoinContestUseCase useCase;

    @Autowired
    CompetitionDefaults competitionDefaults;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SeasonPredictionRepo predictionRepo;

    @Autowired
    EntryRepo entryRepo;

    @MockBean
    Clock clock;

    private UUID competitionId;
    private UUID seasonId;
    private UUID contestId;
    private UUID roundId;
    private UUID userId;

    private Instant now;

    @BeforeAll
    void setupPrerequisites() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        insertCompetitionAndSeason();
        insertContest();
        linkSeasonToContest();
        insertRound(roundId, seasonId, 1, RoundStatus.OPEN);
    }

    @BeforeEach
    void setupMocks() {
        // @MockBean mocks are reset between test methods.
        now = Instant.parse("2024-12-22T10:00:00Z");
        when(clock.instant()).thenReturn(now);

        resetToRound1Open();

        userId = UUID.randomUUID();
        insertUser(userId, "join-user-" + userId + "@example.com");
    }

    @AfterAll
    void cleanup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);
    }

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

        @Test
        @DisplayName("should join contest successfully with valid rankings")
        void shouldJoinContestSuccessfullyWithValidRankings() {
            JoinContestCommand request = validRequestFromInitialRankings();

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            JoinContestResult joinResult = result.get();
            assertThat(joinResult.predictionId()).isNotNull();
            assertThat(joinResult.entryId()).isNotNull();
            assertThat(joinResult.atRoundNumber()).isEqualTo(1);
            assertThat(joinResult.message()).contains("Welcome");
            assertThat(joinResult.message()).contains("Round 1");

            var prediction = predictionRepo.findByUserAndSeason(userId, seasonId);
            assertThat(prediction).isPresent();
            assertThat(prediction.get().getId()).isEqualTo(joinResult.predictionId());
            assertThat(prediction.get().getUserId()).isEqualTo(userId);
            assertThat(prediction.get().getSeasonId()).isEqualTo(seasonId);
            assertThat(prediction.get().getInitialRankings()).hasSize(12);
            assertThat(prediction.get().getCurrentRankings()).hasSize(12);
            assertThat(prediction.get().getInitialRankings()).isEqualTo(prediction.get().getCurrentRankings());
            assertThat(prediction.get().getSwaps()).isEmpty();
            assertThat(prediction.get().getLastSwapAt()).isNull();
            assertThat(prediction.get().getAtRoundNumber()).isEqualTo(joinResult.atRoundNumber());

            var entry = entryRepo.findByUserAndContest(userId, contestId);
            assertThat(entry).isPresent();
            assertThat(entry.get().getId()).isEqualTo(joinResult.entryId());
            assertThat(entry.get().getUserId()).isEqualTo(userId);
            assertThat(entry.get().getContestId()).isEqualTo(contestId);
        }

        @Test
        @DisplayName("should set at_round_number to current round when round is OPEN")
        void shouldSetAtRoundNumberToCurrentWhenOpen() {
            updateCurrentRoundStatus(RoundStatus.OPEN);
            JoinContestCommand request = validRequestFromInitialRankings();

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(1);

            var prediction = predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(prediction.getAtRoundNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("should set at_round_number to next round when round is LOCKED")
        void shouldSetAtRoundNumberToNextWhenLocked() {
            updateCurrentRoundStatus(RoundStatus.LOCKED);
            JoinContestCommand request = validRequestFromInitialRankings();

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(2);
            assertThat(result.get().message()).contains("Round 2");

            var prediction = predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(prediction.getAtRoundNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("should set at_round_number to next round when round is FINALISED")
        void shouldSetAtRoundNumberToNextWhenFinalised() {
            updateCurrentRoundStatus(RoundStatus.FINALISED);
            JoinContestCommand request = validRequestFromInitialRankings();

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(2);

            var prediction = predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(prediction.getAtRoundNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("should accept any ranking order (no strategic validation)")
        void shouldAcceptAnyRankingOrder() {
            JoinContestCommand request = new JoinContestCommand(List.of(
                    new JoinContestCommand.TeamRankRequest("WHU", 1),
                    new JoinContestCommand.TeamRankRequest("BRE", 2),
                    new JoinContestCommand.TeamRankRequest("CRY", 3),
                    new JoinContestCommand.TeamRankRequest("BHA", 4),
                    new JoinContestCommand.TeamRankRequest("TOT", 5),
                    new JoinContestCommand.TeamRankRequest("MUN", 6),
                    new JoinContestCommand.TeamRankRequest("NEW", 7),
                    new JoinContestCommand.TeamRankRequest("CHE", 8),
                    new JoinContestCommand.TeamRankRequest("AVL", 9),
                    new JoinContestCommand.TeamRankRequest("LIV", 10),
                    new JoinContestCommand.TeamRankRequest("ARS", 11),
                    new JoinContestCommand.TeamRankRequest("MCI", 12)));

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();

            var prediction = predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            TeamRank first = prediction.getCurrentRankings().getFirst();
            assertThat(first.getCode()).isEqualTo("WHU");
            assertThat(first.getPosition()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Validation Errors")
    class ValidationErrors {

        @Test
        @DisplayName("should reject when invalid team count")
        void shouldRejectWhenInvalidTeamCount() {
            JoinContestCommand request = new JoinContestCommand(List.of(
                    new JoinContestCommand.TeamRankRequest("ARS", 1),
                    new JoinContestCommand.TeamRankRequest("LIV", 2)));

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(JoinContestError.InvalidTeamCount.class);
        }

        @Test
        @DisplayName("should reject when invalid team codes")
        void shouldRejectWhenInvalidTeamCodes() {
            List<JoinContestCommand.TeamRankRequest> rankings = INITIAL_RANKINGS.stream()
                    .map(tr -> new JoinContestCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
                    .toList();

            // Replace one code with an invalid code but keep team count correct.
            rankings = new java.util.ArrayList<>(rankings);
            rankings.set(0, new JoinContestCommand.TeamRankRequest("XXX", 1));

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, new JoinContestCommand(rankings));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(JoinContestError.InvalidTeamCodes.class);
        }

        @Test
        @DisplayName("should reject when duplicate positions")
        void shouldRejectWhenDuplicatePositions() {
            List<JoinContestCommand.TeamRankRequest> rankings = INITIAL_RANKINGS.stream()
                    .map(tr -> new JoinContestCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
                    .toList();

            rankings = new ArrayList<>(rankings);
            rankings.set(0, new JoinContestCommand.TeamRankRequest("MCI", 1));
            rankings.set(1, new JoinContestCommand.TeamRankRequest("ARS", 1));

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, new JoinContestCommand(rankings));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(JoinContestError.DuplicatePositions.class);
            assertThat(((JoinContestError.DuplicatePositions) result.getLeft()).duplicates()).contains(1);
        }

        @Test
        @DisplayName("should reject when duplicate team codes")
        void shouldRejectWhenDuplicateTeamCodes() {
            List<JoinContestCommand.TeamRankRequest> rankings = INITIAL_RANKINGS.stream()
                    .map(tr -> new JoinContestCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
                    .toList();

            rankings = new ArrayList<>(rankings);
            rankings.set(0, new JoinContestCommand.TeamRankRequest("MCI", 1));
            rankings.set(1, new JoinContestCommand.TeamRankRequest("MCI", 2));

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, new JoinContestCommand(rankings));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(JoinContestError.DuplicateTeamCodes.class);
            assertThat(((JoinContestError.DuplicateTeamCodes) result.getLeft()).duplicates()).contains("MCI");
        }

        @Test
        @DisplayName("should reject when season ended (cannot join last round if not OPEN)")
        void shouldRejectWhenSeasonEnded() {
            setCurrentRoundTo(22, RoundStatus.LOCKED);
            setSeasonCurrentRound(roundId, 22);

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, validRequestFromInitialRankings());

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(JoinContestError.SeasonEnded.class);
            assertThat(((JoinContestError.SeasonEnded) result.getLeft()).currentRound()).isEqualTo(22);
            assertThat(((JoinContestError.SeasonEnded) result.getLeft()).maxRounds()).isEqualTo(22);
        }

        @Test
        @DisplayName("should allow joining last round if it is OPEN")
        void shouldAllowJoiningLastRoundIfOpen() {
            setCurrentRoundTo(22, RoundStatus.OPEN);
            setSeasonCurrentRound(roundId, 22);

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, validRequestFromInitialRankings());

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(22);
        }

        @Test
        @DisplayName("should reject when already joined")
        void shouldRejectWhenAlreadyJoined() {
            JoinContestCommand request = validRequestFromInitialRankings();

            Either<JoinContestError, JoinContestResult> first = useCase.execute(userId, request);
            assertThat(first.isRight()).isTrue();

            Either<JoinContestError, JoinContestResult> second = useCase.execute(userId, request);

            assertThat(second.isLeft()).isTrue();
            assertThat(second.getLeft()).isInstanceOf(JoinContestError.AlreadyJoined.class);
            assertThat(((JoinContestError.AlreadyJoined) second.getLeft()).existingPredictionId())
                    .isEqualTo(first.get().predictionId());
        }
    }

    private static JoinContestCommand validRequestFromInitialRankings() {
        return new JoinContestCommand(
                INITIAL_RANKINGS.stream()
                        .map(tr -> new JoinContestCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
                        .toList());
    }

    private void insertCompetitionAndSeason() {
        String competitionSlug = competitionDefaults.defaultCompetitionSlug();
        jdbcTemplate.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?, '[]'::jsonb, ?)",
                competitionId,
                "Premier League",
            competitionSlug,
                "PL",
                seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed, fk_current_round_id, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                SEASON_SLUG,
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                22,
                12,
                initialRankingsJson(),
                false,
                roundId,
                1);
    }

    private void insertContest() {
        jdbcTemplate.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestId,
                seasonId,
                "Main League",
                false,
                null,
                1,
                22,
                null);
    }

    private void linkSeasonToContest() {
        jdbcTemplate.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);
    }

    private void insertRound(UUID id, UUID seasonId, int position, RoundStatus status) {
        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_status) VALUES (?,?,?,?,?,?)",
                id,
                seasonId,
                "Round " + position,
                "round-" + position,
                position,
                status.name());
    }

    private void updateCurrentRoundStatus(RoundStatus status) {
        jdbcTemplate.update(
                "UPDATE t_round SET c_status = ? WHERE pk_id = ?",
                status.name(),
                roundId);
    }

    private void setCurrentRoundTo(int position, RoundStatus status) {
        jdbcTemplate.update(
                "UPDATE t_round SET c_name = ?, c_slug = ?, c_position = ?, c_status = ? WHERE pk_id = ?",
                "Round " + position,
                "round-" + position,
                position,
                status.name(),
                roundId);
    }

    private void resetToRound1Open() {
        setCurrentRoundTo(1, RoundStatus.OPEN);
        setSeasonCurrentRound(roundId, 1);
    }

    private void setSeasonCurrentRound(UUID currentRoundId, int matchDay) {
        jdbcTemplate.update(
                "UPDATE t_season SET fk_current_round_id = ?, c_current_match_day = ? WHERE pk_id = ?",
                currentRoundId,
                matchDay,
                seasonId);
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                id,
                email,
                "test-password-hash",
                "Join User",
                randomPublicId(),
                true);

        jdbcTemplate.update(
                "INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)",
                id,
                "PLAYER");
    }

    private static String initialRankingsJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < INITIAL_RANKINGS.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            TeamRank tr = INITIAL_RANKINGS.get(i);
            sb.append("{\"code\":\"").append(tr.getCode()).append("\",\"position\":").append(tr.getPosition()).append("}");
        }
        sb.append("]");
        return sb.toString();
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
