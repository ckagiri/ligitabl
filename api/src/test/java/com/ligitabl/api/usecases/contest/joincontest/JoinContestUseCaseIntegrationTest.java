package com.ligitabl.api.usecases.contest.joincontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
        @DisplayName("should join contest and create prediction + entry with real database")
        void shouldJoinContest() {
            JoinContestRequest request = validRequestFromInitialRankings();

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().atRoundNumber()).isEqualTo(1);

            var prediction = predictionRepo.findByUserAndSeason(userId, seasonId);
            assertThat(prediction).isPresent();

            var entry = entryRepo.findByUserAndContest(userId, contestId);
            assertThat(entry).isPresent();
            assertThat(entry.get().getId()).isEqualTo(result.get().entryId());
        }
    }

    @Nested
    @DisplayName("Validation Errors")
    class ValidationErrors {

        @Test
        @DisplayName("should reject when invalid team count")
        void shouldRejectWhenInvalidTeamCount() {
            JoinContestRequest request = new JoinContestRequest(List.of(
                    new JoinContestRequest.TeamRankRequest("ARS", 1),
                    new JoinContestRequest.TeamRankRequest("LIV", 2)));

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(JoinContestError.InvalidTeamCount.class);
        }

        @Test
        @DisplayName("should reject when invalid team codes")
        void shouldRejectWhenInvalidTeamCodes() {
            List<JoinContestRequest.TeamRankRequest> rankings = INITIAL_RANKINGS.stream()
                    .map(tr -> new JoinContestRequest.TeamRankRequest(tr.getCode(), tr.getPosition()))
                    .toList();

            // Replace one code with an invalid code but keep team count correct.
            rankings = new java.util.ArrayList<>(rankings);
            rankings.set(0, new JoinContestRequest.TeamRankRequest("XXX", 1));

            Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, new JoinContestRequest(rankings));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(JoinContestError.InvalidTeamCodes.class);
        }

        @Test
        @DisplayName("should reject when already joined")
        void shouldRejectWhenAlreadyJoined() {
            JoinContestRequest request = validRequestFromInitialRankings();

            Either<JoinContestError, JoinContestResult> first = useCase.execute(userId, request);
            assertThat(first.isRight()).isTrue();

            Either<JoinContestError, JoinContestResult> second = useCase.execute(userId, request);

            assertThat(second.isLeft()).isTrue();
            assertThat(second.getLeft()).isInstanceOf(JoinContestError.AlreadyJoined.class);
            assertThat(((JoinContestError.AlreadyJoined) second.getLeft()).existingPredictionId())
                    .isEqualTo(first.get().predictionId());
        }
    }

    private static JoinContestRequest validRequestFromInitialRankings() {
        return new JoinContestRequest(
                INITIAL_RANKINGS.stream()
                        .map(tr -> new JoinContestRequest.TeamRankRequest(tr.getCode(), tr.getPosition()))
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
