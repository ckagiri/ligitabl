package com.ligitabl.api.usecases.competition;

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
class GetCompetitionAndSeasonsIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

        @Autowired
        JdbcTemplate jdbcTemplate;

        @BeforeEach
        void setupData() {
        jdbcTemplate.update("DELETE FROM t_round");
        jdbcTemplate.update("DELETE FROM t_season");
        jdbcTemplate.update("DELETE FROM t_competition");

        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();

        jdbcTemplate.update(
            "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code) VALUES (?,?,?,?)",
            competitionId,
            "Premier League",
            "premier-league",
            "PL");

        jdbcTemplate.update(
            "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_current_match_day) "
                + "VALUES (?,?,?,?,?,?,?,?,?)",
            seasonId,
            1,
            competitionId,
            "2024/25",
            "2024-25",
            LocalDate.of(2024, 8, 1),
            LocalDate.of(2025, 5, 31),
            38,
            1);
        }

    @Test
    void getCompetitions_shouldReturnAtLeastOneCompetition() {
        var url = "http://localhost:" + port + "/api/competitions";
        @SuppressWarnings("unchecked")
        ResponseEntity<List> response = (ResponseEntity<List>) restTemplate.getForEntity(url, List.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void getSeasons_shouldReturnSeasonForCompetition() {
        var url =
                "http://localhost:" + port + "/api/competitions/premier-league/seasons";
        @SuppressWarnings("unchecked")
        ResponseEntity<List> response = (ResponseEntity<List>) restTemplate.getForEntity(url, List.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getSeasonBySlug_shouldReturnSingleSeason() {
        var url =
                "http://localhost:" + port
                        + "/api/competitions/premier-league/seasons/2024-25";
        ResponseEntity<Object> response = restTemplate.getForEntity(url, Object.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
    }
}
