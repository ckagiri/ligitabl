package com.ligitabl.api.usecases.standings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.StandingsRepo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StandingsEndpointsIT extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StandingsRepo standingsRepo;

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
    }

    @Test
    void getCurrentRoundStandings_shouldReturnStandings() {
        var rankings = List.of(
                StandingsTeamRank.builder()
                        .ranking(new TeamRank("HOM", 1))
                        .metadata(new StandingsMetadata(1, 1, 0, 0, 3, 3, 0, 3))
                        .build(),
                StandingsTeamRank.builder()
                        .ranking(new TeamRank("AWY", 2))
                        .metadata(new StandingsMetadata(1, 0, 0, 1, 0, 0, 3, 0))
                        .build());

        standingsRepo.save(Standings.builder()
                .seasonId(seasonId)
                .roundPosition(1)
                .rankings(rankings)
                .finalised(true)
                .finalisedAt(OffsetDateTime.now())
                .build());

        String url = "http://localhost:" + port + "/api/rounds/current/standings?competition=premier-league";
        ResponseEntity<StandingsEntryDto[]> response = restTemplate.getForEntity(url, StandingsEntryDto[].class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()[0].getTeamName()).isEqualTo("Home Team");
        assertThat(response.getBody()[0].getPosition()).isEqualTo(1);
    }

    @Test
    void getCurrentRoundStandings_whenNoStandingsExists_returnsEmptyArray() {
        String url = "http://localhost:" + port + "/api/rounds/current/standings?competition=premier-league";
        ResponseEntity<Object[]> response = restTemplate.getForEntity(url, Object[].class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getRoundStandingsByPosition_invalidPosition_returns400() {
        String url = "http://localhost:" + port + "/api/rounds/0/standings?competition=premier-league";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
