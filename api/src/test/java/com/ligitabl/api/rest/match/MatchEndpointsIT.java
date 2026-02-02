package com.ligitabl.api.rest.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.MatchStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchEndpointsIT extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;
    private UUID homeTeamId;
    private UUID awayTeamId;

    @BeforeEach
    void setupData() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        homeTeamId = UUID.randomUUID();
        awayTeamId = UUID.randomUUID();

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

        jdbcTemplate.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, fk_active_season_id) VALUES (?,?,?,?,?)",
                competitionId,
                "Premier League",
                "premier-league",
                "PL",
                seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, fk_current_round_id, c_current_match_day) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                38,
                roundId,
                1);

        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position) VALUES (?,?,?,?,?)",
                roundId,
                seasonId,
                "Matchday 1",
                "md-1",
                1);

        jdbcTemplate.update(
                "INSERT INTO t_match (pk_id, c_client_id, fk_round_id, fk_home_team_id, fk_away_team_id, c_slug, c_status, c_kick_off, c_venue, c_matchday) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                1,
                roundId,
                homeTeamId,
                awayTeamId,
                "home-vs-away",
                MatchStatus.SCHEDULED.name(),
                OffsetDateTime.now(),
                "Sample Stadium",
                1);
    }

    @Test
    void getMatchesForRound_shouldReturnMatches() {
        String url = "http://localhost:" + port + "/api/competitions/premier-league/seasons/2024-25/rounds/1/matches";
        ResponseEntity<Object[]> response = restTemplate.getForEntity(url, Object[].class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getDefaultRoundMatches_shouldReturnMatches() {
        String url = "http://localhost:" + port + "/api/rounds/default/matches";
        ResponseEntity<Object[]> response = restTemplate.getForEntity(url, Object[].class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
    }
}
