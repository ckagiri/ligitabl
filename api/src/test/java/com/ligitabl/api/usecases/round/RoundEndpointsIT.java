package com.ligitabl.api.usecases.round;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.testsupport.PostgresContainerConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresContainerConfig.class)
class RoundEndpointsIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private UUID competitionId;
    private UUID seasonId;

    @BeforeEach
    void setupData() {
        jdbcTemplate.update("DELETE FROM t_round");
        jdbcTemplate.update("DELETE FROM t_season");
        jdbcTemplate.update("DELETE FROM t_competition");

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        jdbcTemplate.update(
            "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code) VALUES (?,?,?,?)",
            competitionId,
            "Premier League",
            "premier-league",
            "PL");

        jdbcTemplate.update(
            "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds) "
                + "VALUES (?,?,?,?,?,?,?,?)",
            seasonId,
            1,
            competitionId,
            "2024/25",
            "2024-25",
            LocalDate.of(2024, 8, 1),
            LocalDate.of(2025, 5, 31),
            38);

        jdbcTemplate.update(
            "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position) VALUES (?,?,?,?,?)",
            UUID.randomUUID(),
            seasonId,
            "Matchday 1",
            "md-1",
            1);
    }

    @Test
    void getRounds_shouldReturnRound() {
        String url = "http://localhost:" + port + "/api/competitions/premier-league/seasons/2024-25/rounds";
        @SuppressWarnings("unchecked")
        ResponseEntity<List> response = (ResponseEntity<List>) restTemplate.getForEntity(url, List.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getRoundByPosition_shouldReturnSingleRound() {
        String url =
                "http://localhost:" + port + "/api/competitions/premier-league/seasons/2024-25/rounds/1";
        ResponseEntity<Object> response = restTemplate.getForEntity(url, Object.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
    }
}
