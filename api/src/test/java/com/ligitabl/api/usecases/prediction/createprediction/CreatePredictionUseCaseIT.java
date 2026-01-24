package com.ligitabl.api.usecases.prediction.createprediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@DisplayName("CreatePredictionUseCase Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreatePredictionUseCaseIT extends AbstractPostgresIT {

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
    CreatePredictionUseCase useCase;

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
    private UUID homeTeamId;
    private UUID awayTeamId;

    private Instant now;

    @BeforeAll
    void setupPrerequisites() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        homeTeamId = UUID.randomUUID();
        awayTeamId = UUID.randomUUID();

        insertCompetitionAndSeason();
        insertContest();
        linkSeasonToContest();
        insertTeamsForMatches();
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
            CreatePredictionCommand request = validRequestFromInitialRankings();

            Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            CreatePredictionResult joinResult = result.get();
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
            assertThat(prediction.get().getInitialRankings())
                    .isEqualTo(prediction.get().getCurrentRankings());
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
            CreatePredictionCommand request = validRequestFromInitialRankings();

            Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(1);

            var prediction =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(prediction.getAtRoundNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("should set at_round_number to next round when round is LOCKED")
        void shouldSetAtRoundNumberToNextWhenLocked() {
            updateCurrentRoundStatus(RoundStatus.LOCKED);
            CreatePredictionCommand request = validRequestFromInitialRankings();

            Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(2);
            assertThat(result.get().message()).contains("Round 2");

            var prediction =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(prediction.getAtRoundNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("should set at_round_number to next round when round is FINALISED")
        void shouldSetAtRoundNumberToNextWhenFinalised() {
            updateCurrentRoundStatus(RoundStatus.FINALISED);
            CreatePredictionCommand request = validRequestFromInitialRankings();

            Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(2);

            var prediction =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(prediction.getAtRoundNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("should accept any ranking order (no strategic validation)")
        void shouldAcceptAnyRankingOrder() {
            CreatePredictionCommand request = new CreatePredictionCommand(List.of(
                    new CreatePredictionCommand.TeamRankRequest("WHU", 1),
                    new CreatePredictionCommand.TeamRankRequest("BRE", 2),
                    new CreatePredictionCommand.TeamRankRequest("CRY", 3),
                    new CreatePredictionCommand.TeamRankRequest("BHA", 4),
                    new CreatePredictionCommand.TeamRankRequest("TOT", 5),
                    new CreatePredictionCommand.TeamRankRequest("MUN", 6),
                    new CreatePredictionCommand.TeamRankRequest("NEW", 7),
                    new CreatePredictionCommand.TeamRankRequest("CHE", 8),
                    new CreatePredictionCommand.TeamRankRequest("AVL", 9),
                    new CreatePredictionCommand.TeamRankRequest("LIV", 10),
                    new CreatePredictionCommand.TeamRankRequest("ARS", 11),
                    new CreatePredictionCommand.TeamRankRequest("MCI", 12)));

            Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();

            var prediction =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
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
            CreatePredictionCommand request = new CreatePredictionCommand(List.of(
                    new CreatePredictionCommand.TeamRankRequest("ARS", 1),
                    new CreatePredictionCommand.TeamRankRequest("LIV", 2)));

            Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, request);

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(CreatePredictionError.InvalidTeamCount.class);
        }

        @Test
        @DisplayName("should reject when invalid team codes")
        void shouldRejectWhenInvalidTeamCodes() {
            List<CreatePredictionCommand.TeamRankRequest> rankings = INITIAL_RANKINGS.stream()
                    .map(tr -> new CreatePredictionCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
                    .toList();

            // Replace one code with an invalid code but keep team count correct.
            rankings = new java.util.ArrayList<>(rankings);
            rankings.set(0, new CreatePredictionCommand.TeamRankRequest("XXX", 1));

            Either<CreatePredictionError, CreatePredictionResult> result =
                    useCase.execute(userId, new CreatePredictionCommand(rankings));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(CreatePredictionError.InvalidTeamCodes.class);
        }

        @Test
        @DisplayName("should reject when duplicate positions")
        void shouldRejectWhenDuplicatePositions() {
            List<CreatePredictionCommand.TeamRankRequest> rankings = INITIAL_RANKINGS.stream()
                    .map(tr -> new CreatePredictionCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
                    .toList();

            rankings = new ArrayList<>(rankings);
            rankings.set(0, new CreatePredictionCommand.TeamRankRequest("MCI", 1));
            rankings.set(1, new CreatePredictionCommand.TeamRankRequest("ARS", 1));

            Either<CreatePredictionError, CreatePredictionResult> result =
                    useCase.execute(userId, new CreatePredictionCommand(rankings));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(CreatePredictionError.DuplicatePositions.class);
            assertThat(((CreatePredictionError.DuplicatePositions) result.getLeft()).duplicates())
                    .contains(1);
        }

        @Test
        @DisplayName("should reject when duplicate team codes")
        void shouldRejectWhenDuplicateTeamCodes() {
            List<CreatePredictionCommand.TeamRankRequest> rankings = INITIAL_RANKINGS.stream()
                    .map(tr -> new CreatePredictionCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
                    .toList();

            rankings = new ArrayList<>(rankings);
            rankings.set(0, new CreatePredictionCommand.TeamRankRequest("MCI", 1));
            rankings.set(1, new CreatePredictionCommand.TeamRankRequest("MCI", 2));

            Either<CreatePredictionError, CreatePredictionResult> result =
                    useCase.execute(userId, new CreatePredictionCommand(rankings));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(CreatePredictionError.DuplicateTeamCodes.class);
            assertThat(((CreatePredictionError.DuplicateTeamCodes) result.getLeft()).duplicates())
                    .contains("MCI");
        }

        @Test
        @DisplayName("should reject when season ended (cannot join last round if not OPEN)")
        void shouldRejectWhenSeasonEnded() {
            setCurrentRoundTo(22, RoundStatus.LOCKED);
            setSeasonCurrentRound(roundId, 22);

            Either<CreatePredictionError, CreatePredictionResult> result =
                    useCase.execute(userId, validRequestFromInitialRankings());

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(CreatePredictionError.Ended.class);
            assertThat(((CreatePredictionError.Ended) result.getLeft()).currentRound())
                    .isEqualTo(22);
            assertThat(((CreatePredictionError.Ended) result.getLeft()).maxRounds())
                    .isEqualTo(22);
        }

        @Test
        @DisplayName("should allow joining last round if it is OPEN")
        void shouldAllowJoiningLastRoundIfOpen() {
            setCurrentRoundTo(22, RoundStatus.OPEN);
            setSeasonCurrentRound(roundId, 22);

            Either<CreatePredictionError, CreatePredictionResult> result =
                    useCase.execute(userId, validRequestFromInitialRankings());

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(22);
        }

        @Test
        @DisplayName("should reject when already joined")
        void shouldRejectWhenAlreadyJoined() {
            CreatePredictionCommand request = validRequestFromInitialRankings();

            Either<CreatePredictionError, CreatePredictionResult> first = useCase.execute(userId, request);
            assertThat(first.isRight()).isTrue();

            Either<CreatePredictionError, CreatePredictionResult> second = useCase.execute(userId, request);

            assertThat(second.isLeft()).isTrue();
            assertThat(second.getLeft()).isInstanceOf(CreatePredictionError.AlreadyJoined.class);
            assertThat(((CreatePredictionError.AlreadyJoined) second.getLeft()).existingPredictionId())
                    .isEqualTo(first.get().predictionId());
        }
    }

    private static CreatePredictionCommand validRequestFromInitialRankings() {
        return new CreatePredictionCommand(INITIAL_RANKINGS.stream()
                .map(tr -> new CreatePredictionCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
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
        boolean isFinalized = status == RoundStatus.FINALISED;
        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) VALUES (?,?,?,?,?,?)",
                id,
                seasonId,
                "Round " + position,
                "round-" + position,
                position,
                isFinalized);
    }

    private void updateCurrentRoundStatus(RoundStatus status) {
        clearMatchesForRound(roundId);
        if (status == RoundStatus.FINALISED) {
            jdbcTemplate.update("UPDATE t_round SET c_is_finalized = true WHERE pk_id = ?", roundId);
            return;
        }

        jdbcTemplate.update("UPDATE t_round SET c_is_finalized = false WHERE pk_id = ?", roundId);
        if (status == RoundStatus.LOCKED) {
            insertLockedMatch(roundId, 1);
        }
    }

    private void setCurrentRoundTo(int position, RoundStatus status) {
        boolean isFinalized = status == RoundStatus.FINALISED;
        jdbcTemplate.update(
                "UPDATE t_round SET c_name = ?, c_slug = ?, c_position = ?, c_is_finalized = ? WHERE pk_id = ?",
                "Round " + position,
                "round-" + position,
                position,
                isFinalized,
                roundId);

        clearMatchesForRound(roundId);
        if (status == RoundStatus.LOCKED) {
            insertLockedMatch(roundId, position);
        }
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

    private void insertTeamsForMatches() {
        jdbcTemplate.update(
                "INSERT INTO t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?)",
                homeTeamId,
                "Home Team",
                "Home",
                "home-team",
                "HOM");

        jdbcTemplate.update(
                "INSERT INTO t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?)",
                awayTeamId,
                "Away Team",
                "Away",
                "away-team",
                "AWY");
    }

    private void clearMatchesForRound(UUID roundId) {
        jdbcTemplate.update("DELETE FROM t_match WHERE fk_round_id = ?", roundId);
    }

    private void insertLockedMatch(UUID roundId, int matchDay) {
        jdbcTemplate.update(
                "INSERT INTO t_match (pk_id, c_client_id, fk_round_id, fk_home_team_id, fk_away_team_id, c_slug, c_status, c_kick_off, c_venue, c_matchday) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                1,
                roundId,
                homeTeamId,
                awayTeamId,
                "home-vs-away-" + matchDay,
                MatchStatus.LIVE.name(),
                OffsetDateTime.now().withNano(0),
                "Test Stadium",
                matchDay);
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

        jdbcTemplate.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
    }

    private static String initialRankingsJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < INITIAL_RANKINGS.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            TeamRank tr = INITIAL_RANKINGS.get(i);
            sb.append("{\"code\":\"")
                    .append(tr.getCode())
                    .append("\",\"position\":")
                    .append(tr.getPosition())
                    .append("}");
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
