package com.ligitabl.api.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthAccessIT extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepo userRepo;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    PublicIdGenerator publicIdGenerator;

    @BeforeEach
    void setUp() {
        // Player user
        UUID playerId = UUID.randomUUID();
        User player = User.builder()
                .id(playerId)
                .publicId(publicIdGenerator.generate(playerId))
                .email(Email.create("player-" + playerId + "@example.com"))
                .displayName("Player")
                .password(passwordHasher.hash(Password.Plaintext.create("player12345")))
                .roles(Set.of(Role.PLAYER))
                .emailVerified(true)
                .build();
        userRepo.create(player);

        // Admin user
        UUID adminId = UUID.randomUUID();
        User admin = User.builder()
                .id(adminId)
                .publicId(publicIdGenerator.generate(adminId))
                .email(Email.create("admin-" + adminId + "@example.com"))
                .displayName("Admin")
                .password(passwordHasher.hash(Password.Plaintext.create("admin12345")))
                .roles(Set.of(Role.ADMIN))
                .emailVerified(true)
                .build();
        userRepo.create(admin);
    }

    @Test
    void playerToken_allowsPlayerEndpoint_and_forbidsAdminEndpoint() {
        String playerEmail = findFirstEmailContaining("player-");
        String token = loginAndGetToken(playerEmail, "player12345");

        ResponseEntity<String> me = getWithBearer("/api/me", token, String.class);
        assertThat(me.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> playerOk = getWithBearer("/api/player", token, String.class);
        assertThat(playerOk.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> adminForbidden = getWithBearer("/api/admin", token, String.class);
        assertThat(adminForbidden.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void adminToken_allowsAdminEndpoint_and_forbidsPlayerEndpoint() {
        String adminEmail = findFirstEmailContaining("admin-");
        String token = loginAndGetToken(adminEmail, "admin12345");

        ResponseEntity<String> adminOk = getWithBearer("/api/admin", token, String.class);
        assertThat(adminOk.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> playerForbidden = getWithBearer("/api/player", token, String.class);
        assertThat(playerForbidden.getStatusCode().value()).isEqualTo(403);
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

    private String findFirstEmailContaining(String contains) {
        // This is a tiny convenience so we don't thread emails through from setUp.
        // It relies on the deterministic prefixes we used when creating users.
        // Query via /api is overkill; just compute by scanning known prefixes in-memory is not possible here.
        // We'll derive by searching DB through the login endpoint by using the same prefixes.
        // In practice, we only need "some" email; we can just build one again with a fresh user.

        UUID id = UUID.randomUUID();
        String email = contains + id + "@example.com";
        User user = User.builder()
                .id(id)
                .publicId(publicIdGenerator.generate(id))
                .email(Email.create(email))
                .displayName("Temp")
                .password(passwordHasher.hash(
                        Password.Plaintext.create(contains.startsWith("admin-") ? "admin12345" : "player12345")))
                .roles(contains.startsWith("admin-") ? Set.of(Role.ADMIN) : Set.of(Role.PLAYER))
                .emailVerified(true)
                .build();
        userRepo.create(user);
        return email;
    }
}
