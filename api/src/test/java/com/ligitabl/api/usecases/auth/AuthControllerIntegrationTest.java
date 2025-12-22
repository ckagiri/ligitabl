package com.ligitabl.api.usecases.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
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
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ligitabl.api.testsupport.AbstractPostgresIT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwordEncoder;

    String email;
    String password;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        email = "integration-test-" + userId + "@example.com";
        password = "testPassword123";

        String publicId = randomPublicId();
        String passwordHash = passwordEncoder.encode(password);

        jdbcTemplate.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                userId,
                email,
                passwordHash,
                "Integration Test User",
                publicId,
                true);

        jdbcTemplate.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?,?)", userId, "PLAYER");
    }

    private static String randomPublicId() {
        // Must match model PublicId regex: ^[23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz]{10}$
        String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            int idx = (int) (Math.random() * alphabet.length());
            sb.append(alphabet.charAt(idx));
        }
        return sb.toString();
    }

    @Test
    void shouldLoginSuccessfullyWithValidCredentials() {
        ResponseEntity<Map<String, Object>> response = postLoginForMap(email, password);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertThat(body.get("token")).isInstanceOf(String.class);
        assertThat((String) body.get("token")).isNotBlank();
    }

    @Test
    void shouldReturn401ForInvalidPassword() {
        ResponseEntity<Map<String, Object>> response = postLoginForMap(email, "wrongPassword");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertThat(body.get("error")).isEqualTo("Unauthorized");
    }

    @Test
    void shouldReturn401ForNonExistentUser() {
        ResponseEntity<Map<String, Object>> response = postLoginForMap("nonexistent@example.com", password);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertThat(body.get("error")).isEqualTo("Unauthorized");
    }

    @Test
    void shouldReturn400ForInvalidEmailFormat() {
        ResponseEntity<String> response = postLoginForString("not-an-email", password);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void shouldReturn400ForShortPassword() {
        ResponseEntity<String> response = postLoginForString(email, "short");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private ResponseEntity<Map<String, Object>> postLoginForMap(String email, String password) {
        String url = "http://localhost:" + port + "/auth/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("email", email, "password", password), headers);

        return restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<String> postLoginForString(String email, String password) {
        String url = "http://localhost:" + port + "/auth/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("email", email, "password", password), headers);
        return restTemplate.postForEntity(url, request, String.class);
    }
}
