package com.ligitabl.api.usecases.prediction.seeding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.api.usecases.prediction.finalizeround.FinalizationResult;
import com.ligitabl.api.usecases.prediction.finalizeround.FinalizeRoundUseCase;

@SpringBootTest
@DisplayName("SeedSeasonUseCase Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeedSeasonUseCaseIntegrationTest extends AbstractPostgresIT {

    @Autowired
    SeedSeasonUseCase seedSeasonUseCase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    SeedingConfigLoader configLoader;

    @MockBean
    FinalizeRoundUseCase finalizeRoundUseCase;

    private SeedSeasonDbFixture fixture;

    @BeforeAll
    void setupPrerequisites() {
        // Keep fixed, readable test data (like .art/testing.md) while still ensuring we start from a clean DB.
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        fixture = SeedSeasonDbFixture.create(jdbcTemplate);
    }

    @BeforeEach
    void setupMocks() {
        // @MockBean mocks are reset between test methods.
        when(configLoader.loadConfig()).thenReturn(fixture.seedingConfig());
        when(finalizeRoundUseCase.execute(any(UUID.class)))
            .thenReturn(
                Either.right(
                    new FinalizationResult(UUID.randomUUID(), 1, 0, 0, false, Instant.now())));
    }

    @AfterAll
    void cleanup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);
    }

    @Test
    @DisplayName("should seed season successfully")
    void shouldSeedSeasonSuccessfully() {
        Either<SeedingError, SeasonSeedResult> result = seedSeasonUseCase.execute();

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().getSeason().getId()).isEqualTo(fixture.seasonId());
        assertThat(result.get().getUsers()).hasSize(3);

        assertThat(fixture.countMatchesForSeason()).isEqualTo(132);
        assertThat(fixture.countPredictionsForSeason()).isEqualTo(3);
        assertThat(fixture.countEntriesForContest()).isEqualTo(3);
    }

    @Test
    @DisplayName("should be idempotent when called twice")
    void shouldBeIdempotentWhenCalledTwice() {
        Either<SeedingError, SeasonSeedResult> r1 = seedSeasonUseCase.execute();
        Either<SeedingError, SeasonSeedResult> r2 = seedSeasonUseCase.execute();

        assertThat(r1.isRight()).isTrue();
        assertThat(r2.isRight()).isTrue();

        assertThat(fixture.countMatchesForSeason()).isEqualTo(132);
        assertThat(fixture.countPredictionsForSeason()).isEqualTo(3);
    }

    /**
     * Keeps the test body focused on Arrange/Act/Assert while still inserting real rows.
     * This intentionally mirrors the structure in `.art/testing.md`.
     */
    private static final class SeedSeasonDbFixture {
        private static final int TOTAL_TEAMS = 12;
        private static final int TOTAL_ROUNDS = 22;
        private static final String SEASON_SLUG = "2024-25";
        private static final String COMPETITION_SLUG = "test-league";

        private static final List<String> TEAM_CODES = List.of(
            "MCI",
            "ARS",
            "LIV",
            "AVL",
            "CHE",
            "NEW",
            "MUN",
            "TOT",
            "BHA",
            "CRY",
            "BRE",
            "WHU");

        private static final List<String> DEMO_EMAILS = List.of(
            "alice@demo.com",
            "bob@demo.com",
            "charlie@demo.com");

        private final JdbcTemplate jdbc;

        private final UUID competitionId;
        private final UUID seasonId;
        private final UUID contestId;
        private final List<String> teamCodes;
        private final List<String> demoEmails;

        private SeedSeasonDbFixture(
                JdbcTemplate jdbc,
                UUID competitionId,
                UUID seasonId,
                UUID contestId,
                List<String> teamCodes,
                List<String> demoEmails) {
            this.jdbc = jdbc;
            this.competitionId = competitionId;
            this.seasonId = seasonId;
            this.contestId = contestId;
            this.teamCodes = teamCodes;
            this.demoEmails = demoEmails;
        }

        static SeedSeasonDbFixture create(JdbcTemplate jdbc) {
            UUID competitionId = UUID.randomUUID();
            UUID seasonId = UUID.randomUUID();
            UUID contestId = UUID.randomUUID();

            List<String> teamCodes = TEAM_CODES;
            List<String> demoEmails = DEMO_EMAILS;

            SeedSeasonDbFixture fixture = new SeedSeasonDbFixture(
                    jdbc,
                    competitionId,
                    seasonId,
                    contestId,
                    teamCodes,
                    demoEmails);

            fixture.insertCompetition();
            fixture.insertTeams();
            fixture.insertSeason();
            fixture.insertContest();
            fixture.linkSeasonToContest();
            fixture.insertRounds();
            fixture.insertDemoUsers();

            return fixture;
        }

        UUID seasonId() {
            return seasonId;
        }

        SeedingConfig seedingConfig() {
            SeedingConfig config = new SeedingConfig();
            config.setCompetitionSlug(COMPETITION_SLUG);
            config.setSeasonSlug(SEASON_SLUG);
            config.setFinishedRounds(0);
            config.setDemoUsers(demoEmails.stream().map(email -> {
                SeedingConfig.DemoUser u = new SeedingConfig.DemoUser();
                u.setEmail(email);
                u.setDisplayName(email.split("@")[0]);
                return u;
            }).toList());
            return config;
        }

        int countMatchesForSeason() {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_match m JOIN t_round r ON m.fk_round_id = r.pk_id WHERE r.fk_season_id = ?",
                    Integer.class,
                    seasonId);
            return Objects.requireNonNull(count, "match count should be present");
        }

        int countPredictionsForSeason() {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_season_prediction WHERE fk_season_id = ?",
                    Integer.class,
                    seasonId);
            return Objects.requireNonNull(count, "prediction count should be present");
        }

        int countEntriesForContest() {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_entry WHERE fk_contest_id = ?",
                    Integer.class,
                    contestId);
            return Objects.requireNonNull(count, "entry count should be present");
        }

        private void insertCompetition() {
            jdbc.update(
                    "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?, '[]'::jsonb, ?)",
                    competitionId,
                    "Test League",
                    COMPETITION_SLUG,
                    "TST",
                    null);
        }

        private void insertTeams() {
            int teamClientIdBase = ThreadLocalRandom.current().nextInt(1_000, 1_000_000);
            for (int i = 0; i < teamCodes.size(); i++) {
                String code = teamCodes.get(i);
                jdbc.update(
                        "INSERT INTO t_team (pk_id, c_client_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?,?)",
                        UUID.randomUUID(),
                        teamClientIdBase + i,
                        "Team " + code,
                        code,
                        "team-" + code.toLowerCase(),
                        code);
            }
        }

        private void insertSeason() {
            int seasonClientId = ThreadLocalRandom.current().nextInt(1_000, 1_000_000);
            jdbc.update(
                    "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_initial_rankings, c_completed, c_total_teams, c_max_hit_points, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?)",
                    seasonId,
                    seasonClientId,
                    competitionId,
                    "Test Season",
                    SEASON_SLUG,
                    LocalDate.of(2024, 8, 1),
                    LocalDate.of(2025, 5, 31),
                    TOTAL_ROUNDS,
                    initialRankingsJson(),
                    false,
                    TOTAL_TEAMS,
                    220,
                    0);
        }

        private void insertContest() {
            jdbc.update(
                    "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                    contestId,
                    seasonId,
                    "Main League",
                    false,
                    null,
                    1,
                    TOTAL_ROUNDS,
                    null);
        }

        private void linkSeasonToContest() {
            jdbc.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);
        }

        private void insertRounds() {
            for (int i = 1; i <= TOTAL_ROUNDS; i++) {
                jdbc.update(
                        "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_status) VALUES (?,?,?,?,?,?)",
                        UUID.randomUUID(),
                        seasonId,
                        "Round " + i,
                        "round-" + i,
                        i,
                        "OPEN");
            }
        }

        private void insertDemoUsers() {
            for (int i = 0; i < demoEmails.size(); i++) {
                UUID userId = UUID.randomUUID();
                String email = demoEmails.get(i);

                jdbc.update(
                        "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                        userId,
                        email,
                        "test-password-hash",
                        "Demo User " + (i + 1),
                        randomPublicId(),
                        true);

                jdbc.update(
                        "INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)",
                        userId,
                        "PLAYER");
            }
        }

        private String initialRankingsJson() {
            StringBuilder rankingsJson = new StringBuilder("[");
            for (int i = 0; i < teamCodes.size(); i++) {
                if (i > 0) {
                    rankingsJson.append(",");
                }
                rankingsJson.append("{\"code\":\"")
                        .append(teamCodes.get(i))
                        .append("\",\"position\":")
                        .append(i + 1)
                        .append("}");
            }
            rankingsJson.append("]");
            return rankingsJson.toString();
        }

        private static String randomPublicId() {
            // Must match model PublicId regex: ^[23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz]{10}$
            String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                int idx = ThreadLocalRandom.current().nextInt(alphabet.length());
                sb.append(alphabet.charAt(idx));
            }
            return sb.toString();
        }

    }
}
