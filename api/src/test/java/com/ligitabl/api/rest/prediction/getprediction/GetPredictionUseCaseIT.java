package com.ligitabl.api.rest.prediction.getprediction;

import static com.ligitabl.api.testsupport.TestIds.randomPublicId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.shared.RankingSource;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.TeamRank;

@SpringBootTest
@DisplayName("GetPredictionUseCase Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetPredictionUseCaseIT extends AbstractPostgresIT {

    private static final String SEASON_SLUG = "2024-25";

    private static final List<TeamRank> INITIAL_RANKINGS =
            List.of(new TeamRank("MCI", 1), new TeamRank("ARS", 2), new TeamRank("LIV", 3), new TeamRank("AVL", 4));

    @Autowired
    GetPredictionUseCase useCase;

    @Autowired
    CompetitionDefaults competitionDefaults;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    Clock clock;

    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;
    private UUID userId;

    @BeforeAll
    void setupPrerequisites() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        userId = UUID.randomUUID();

        insertCompetitionAndSeason();
        insertRound(roundId, seasonId, 1);
        insertTeams();
        insertUser(userId, "prediction-user-" + userId + "@example.com");
    }

    @BeforeEach
    void setupMocks() {
        when(clock.instant()).thenReturn(java.time.Instant.parse("2024-12-22T10:00:00Z"));
    }

    @AfterAll
    void cleanup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);
    }

    @Test
    void should_fallback_to_baseline_for_anonymous_user() {
        var result = useCase.execute(null);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().rankingSource()).isEqualTo(RankingSource.SEASON_BASELINE);
        assertThat(result.get().rankings()).hasSize(INITIAL_RANKINGS.size());
    }

    @Test
    void should_return_user_prediction_when_present() {
        insertSeasonPrediction();

        var result = useCase.execute(userId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().rankingSource()).isEqualTo(RankingSource.USER_PREDICTION);
        assertThat(result.get().predictionId()).isNotNull();
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
                INITIAL_RANKINGS.size(),
                initialRankingsJson(),
                false,
                roundId,
                1);
    }

    private void insertRound(UUID id, UUID seasonId, int position) {
        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) VALUES (?,?,?,?,?,?)",
                id,
                seasonId,
                "Round " + position,
                "round-" + position,
                position,
                false);
    }

    private void insertTeams() {
        for (TeamRank rank : INITIAL_RANKINGS) {
            jdbcTemplate.update(
                    "INSERT INTO t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    rank.getCode() + " FC",
                    rank.getCode(),
                    rank.getCode().toLowerCase() + "-fc",
                    rank.getCode());
        }
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                id,
                email,
                "test-password-hash",
                "Prediction User",
                randomPublicId(),
                true);

        jdbcTemplate.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
    }

    private void insertSeasonPrediction() {
        jdbcTemplate.update(
                "INSERT INTO t_season_prediction (pk_id, fk_user_id, fk_season_id, c_initial_rankings, c_current_rankings, c_swaps, c_last_swap_at, c_at_round_number) "
                        + "VALUES (?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?)",
                UUID.randomUUID(),
                userId,
                seasonId,
                initialRankingsJson(),
                initialRankingsJson(),
                "[]",
                null,
                1);
    }

    private String initialRankingsJson() {
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
}
