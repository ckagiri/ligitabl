package com.ligitabl.api.rest.match.matchadmin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchAdminControllerIntegrationTest extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepo userRepo;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    PublicIdGenerator publicIdGenerator;

    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;
    private UUID contestId;
    private UUID homeTeamId;
    private UUID awayTeamId;

    private String adminEmail;
    private String adminPassword;
    private String adminToken;

    @BeforeEach
    void setUp() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        homeTeamId = UUID.randomUUID();
        awayTeamId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO t_team (pk_id, c_client_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?,?)",
                homeTeamId,
                1,
                "Arsenal",
                "Arsenal",
                "arsenal",
                "ARS");

        jdbcTemplate.update(
                "INSERT INTO t_team (pk_id, c_client_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?,?)",
                awayTeamId,
                2,
                "Chelsea",
                "Chelsea",
                "chelsea",
                "CHE");

        jdbcTemplate.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, fk_active_season_id) VALUES (?,?,?,?,?)",
                competitionId,
                "Premier League",
                "premier-league",
                "PL",
                seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, fk_current_round_id, c_current_match_day, fk_main_contest_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                38,
                roundId,
                1,
                null);

        jdbcTemplate.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestId,
                seasonId,
                "Main Contest",
                false,
                null,
                1,
                38,
                1_000);

        jdbcTemplate.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) VALUES (?,?,?,?,?,?)",
                roundId,
                seasonId,
                "Matchday 1",
                "md-1",
                1,
                false);

        // Admin user + token
        UUID adminId = UUID.randomUUID();
        adminEmail = "admin-" + adminId + "@example.com";
        adminPassword = "admin12345";
        User admin = User.builder()
                .id(adminId)
                .publicId(publicIdGenerator.generate(adminId))
                .email(Email.create(adminEmail))
                .displayName("Admin")
                .password(passwordHasher.hash(Password.Plaintext.create(adminPassword)))
                .roles(Set.of(Role.ADMIN))
                .emailVerified(true)
                .build();
        userRepo.create(admin);

        adminToken = loginAndGetToken(adminEmail, adminPassword);
    }

    @Test
    void getMatchDetails_existingMatch_returnsDetails_and_actions() {
        String slug = "arsenal-vs-chelsea";
        jdbcTemplate.update(
                "INSERT INTO t_match (pk_id, c_client_id, fk_round_id, fk_season_id, fk_home_team_id, fk_away_team_id, c_slug, c_status, c_kick_off, c_venue, c_matchday) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                1,
                roundId,
                seasonId,
                homeTeamId,
                awayTeamId,
                slug,
                MatchStatus.SCHEDULED.name(),
                OffsetDateTime.now(),
                "Emirates",
                1);

        ResponseEntity<Map> response = getWithBearer(
                "/api/admin/rounds/current/matches/" + slug + "?competition=premier-league", adminToken, Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();

        // Basic shape checks (DTO is returned directly, not wrapped)
        assertThat(response.getBody().get("matchSlug")).isEqualTo(slug);
        assertThat(response.getBody().get("status")).isEqualTo("SCHEDULED");
        assertThat(response.getBody().get("roundPosition")).isEqualTo(1);
        assertThat(response.getBody().get("availableActions")).isInstanceOfAny(java.util.List.class);
    }

    private String loginAndGetToken(String email, String password) {
        String url = "http://localhost:" + port + "/api/auth/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(Map.of("email", email, "password", password), headers);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response =
                (ResponseEntity) restTemplate.postForEntity(url, request, Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("token")).isInstanceOf(String.class);
        return (String) response.getBody().get("token");
    }

    private <T> ResponseEntity<T> getWithBearer(String path, String token, Class<T> responseType) {
        String url = "http://localhost:" + port + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, headers), responseType);
    }
}
