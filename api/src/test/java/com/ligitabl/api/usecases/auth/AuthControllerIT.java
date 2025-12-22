package com.ligitabl.api.usecases.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
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
class AuthControllerIT extends AbstractPostgresIT {

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

    String email;
    String password;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        email = "integration-test-" + userId + "@example.com";
        password = "testPassword123";

        User user = User.builder()
                .id(userId)
                .publicId(publicIdGenerator.generate(userId))
                .email(Email.create(email))
                .displayName("Integration Test User")
                .password(passwordHasher.hash(Password.Plaintext.create(password)))
                .roles(Set.of(Role.PLAYER))
                .emailVerified(true)
                .build();

        userRepo.create(user);
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
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("email", email, "password", password), headers);

        return restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<String> postLoginForString(String email, String password) {
        String url = "http://localhost:" + port + "/auth/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("email", email, "password", password), headers);
        return restTemplate.postForEntity(url, request, String.class);
    }
}
