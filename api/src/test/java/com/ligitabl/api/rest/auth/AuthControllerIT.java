package com.ligitabl.api.rest.auth;

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
        ResponseEntity<AuthDto.ErrorResponse> response = postLoginForError(email, "wrongPassword");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        AuthDto.ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertThat(body.code()).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    void shouldReturn401ForNonExistentUser() {
        ResponseEntity<AuthDto.ErrorResponse> response = postLoginForError("nonexistent@example.com", password);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        AuthDto.ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertThat(body.code()).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    void shouldReturn400ForInvalidEmailFormat() {
        ResponseEntity<AuthDto.ErrorResponse> response = postLoginForError("not-an-email", password);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        AuthDto.ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldReturn400ForShortPassword() {
        ResponseEntity<AuthDto.ErrorResponse> response = postLoginForError(email, "short");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        AuthDto.ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldRegisterNewUserSuccessfully() {
        String newEmail = "newuser-" + UUID.randomUUID() + "@example.com";
        String newPassword = "newPassword123";

        ResponseEntity<Map<String, Object>> response = postRegisterForMap(newEmail, "New User", newPassword);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertThat(body.get("publicId")).isInstanceOf(String.class);
        assertThat((String) body.get("publicId")).isNotBlank();
        assertThat(body.get("email")).isEqualTo(newEmail);
        assertThat(body.get("displayName")).isEqualTo("New User");
        assertThat(body.get("roles")).isInstanceOfAny(java.util.Collection.class);
    }

    @Test
    void shouldReturn409WhenEmailAlreadyExists() {
        ResponseEntity<AuthDto.ErrorResponse> response = postRegisterForError(email, "Another User", "password123");

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        AuthDto.ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertThat(body.code()).isEqualTo("ALREADY_EXISTS");
    }

    @Test
    void shouldReturn400ForInvalidEmailInRegistration() {
        ResponseEntity<AuthDto.ErrorResponse> response =
                postRegisterForError("not-an-email", "Test User", "password123");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        AuthDto.ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldReturn400ForShortPasswordInRegistration() {
        ResponseEntity<AuthDto.ErrorResponse> response =
                postRegisterForError("newuser-" + UUID.randomUUID() + "@example.com", "Test User", "short");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        AuthDto.ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldReturn400ForShortDisplayName() {
        ResponseEntity<AuthDto.ErrorResponse> response =
                postRegisterForError("newuser-" + UUID.randomUUID() + "@example.com", "A", "password123");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        AuthDto.ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldLoginAfterRegistration() {
        String newEmail = "register-then-login-" + UUID.randomUUID() + "@example.com";
        String newPassword = "testPassword123";

        ResponseEntity<Map<String, Object>> registerResponse = postRegisterForMap(newEmail, "Test User", newPassword);
        assertThat(registerResponse.getStatusCode().value()).isEqualTo(201);

        ResponseEntity<Map<String, Object>> loginResponse = postLoginForMap(newEmail, newPassword);
        assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = loginResponse.getBody();
        assertNotNull(body);
        assertThat(body.get("token")).isInstanceOf(String.class);
        assertThat((String) body.get("token")).isNotBlank();
    }

    private ResponseEntity<Map<String, Object>> postLoginForMap(String email, String password) {
        String url = "http://localhost:" + port + "/api/auth/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(Map.of("email", email, "password", password), headers);

        return restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<AuthDto.ErrorResponse> postLoginForError(String email, String password) {
        String url = "http://localhost:" + port + "/api/auth/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(Map.of("email", email, "password", password), headers);

        return restTemplate.exchange(url, HttpMethod.POST, request, AuthDto.ErrorResponse.class);
    }

    private ResponseEntity<Map<String, Object>> postRegisterForMap(String email, String displayName, String password) {
        String url = "http://localhost:" + port + "/api/auth/register";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of(
                        "email",
                        email,
                        "displayName",
                        displayName,
                        "password",
                        password,
                        "turnstileToken",
                        "test-turnstile-token"),
                headers);

        return restTemplate.exchange(url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<AuthDto.ErrorResponse> postRegisterForError(
            String email, String displayName, String password) {
        String url = "http://localhost:" + port + "/api/auth/register";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of(
                        "email",
                        email,
                        "displayName",
                        displayName,
                        "password",
                        password,
                        "turnstileToken",
                        "test-turnstile-token"),
                headers);

        return restTemplate.exchange(url, HttpMethod.POST, request, AuthDto.ErrorResponse.class);
    }
}
