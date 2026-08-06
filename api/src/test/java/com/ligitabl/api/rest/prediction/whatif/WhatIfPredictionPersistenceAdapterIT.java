package com.ligitabl.api.rest.prediction.whatif;

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

import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.WhatIfPrediction;
import com.ligitabl.model.domain.WhatIfScore;
import com.ligitabl.model.repo.WhatIfPredictionRepo;

@SpringBootTest
@DisplayName("WhatIfPredictionPersistenceAdapter Integration Tests")
class WhatIfPredictionPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WhatIfPredictionRepo whatIfPredictionRepo;

    private UUID seasonId;
    private UUID roundId;
    private UUID userId;
    private final UUID matchId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        userId = UUID.randomUUID();

        insertCompetitionAndSeason();
        insertRound();
        insertUser();
    }

    @Test
    @DisplayName("save inserts a new row when none exists for the user + round")
    void save_insertsNewRow() {
        WhatIfPrediction saved = whatIfPredictionRepo.save(prediction(List.of(new WhatIfScore(matchId, 2, 1))));

        assertThat(saved.getId()).isNotNull();
        assertThat(rowCount()).isEqualTo(1);

        var found = whatIfPredictionRepo.findByUserAndRound(userId, roundId);
        assertThat(found).isPresent();
        assertThat(found.get().getScores()).containsExactly(new WhatIfScore(matchId, 2, 1));
    }

    @Test
    @DisplayName("save upserts in place on a second apply for the same user + round")
    void save_updatesExistingRowOnConflict() {
        WhatIfPrediction first = whatIfPredictionRepo.save(prediction(List.of(new WhatIfScore(matchId, 2, 1))));

        WhatIfPrediction second = whatIfPredictionRepo.save(prediction(List.of(new WhatIfScore(matchId, 0, 3))));

        assertThat(rowCount()).isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getScores()).containsExactly(new WhatIfScore(matchId, 0, 3));

        var found = whatIfPredictionRepo.findByUserAndRound(userId, roundId);
        assertThat(found).isPresent();
        assertThat(found.get().getScores()).containsExactly(new WhatIfScore(matchId, 0, 3));
    }

    @Test
    @DisplayName("findByUserAndRound returns empty when nothing saved")
    void findByUserAndRound_emptyWhenAbsent() {
        assertThat(whatIfPredictionRepo.findByUserAndRound(userId, roundId)).isEmpty();
    }

    @Test
    @DisplayName("deleteByUserId removes the user's rows")
    void deleteByUserId_removesRows() {
        whatIfPredictionRepo.save(prediction(List.of(new WhatIfScore(matchId, 2, 1))));

        whatIfPredictionRepo.deleteByUserId(userId);

        assertThat(rowCount()).isZero();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private WhatIfPrediction prediction(List<WhatIfScore> scores) {
        return WhatIfPrediction.builder()
                .userId(userId)
                .roundId(roundId)
                .scores(scores)
                .build();
    }

    private int rowCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_what_if_prediction", Integer.class);
        return count == null ? 0 : count;
    }

    private void insertCompetitionAndSeason() {
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
                TestCalendar.SEASON_NAME,
                TestCalendar.SEASON_SLUG,
                TestCalendar.SEASON_START,
                TestCalendar.SEASON_END,
                38,
                12,
                "[]",
                false,
                null,
                1);
    }

    private void insertRound() {
        jdbc.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized, c_advanced) VALUES (?,?,?,?,?,?,?)",
                roundId,
                seasonId,
                "Round 1",
                "round-1",
                1,
                false,
                false);
    }

    private void insertUser() {
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                userId,
                "whatif@test.com",
                "hash",
                "WhatIfUser",
                "WHATIF1234",
                true);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?,?)", userId, "PLAYER");
    }
}
