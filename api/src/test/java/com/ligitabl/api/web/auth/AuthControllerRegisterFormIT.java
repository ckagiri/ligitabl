package com.ligitabl.api.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.ligitabl.api.client.TurnstileClient;
import com.ligitabl.api.client.TurnstileError;
import com.ligitabl.api.client.turnstile.TurnstileVerifyResponse;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.repo.UserRepo;

/**
 * Covers the web MVC registration form (POST /auth/register), as distinct from the JSON REST API
 * at POST /api/auth/register — the two endpoints share RegisterUseCase but have separate Turnstile
 * wiring and separate error-response shapes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerRegisterFormIT extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepo userRepo;

    // Mocked so these tests never make a real network call to Cloudflare — TurnstileClient's own
    // HTTP behavior is covered by TurnstileClientTest (WireMock).
    @MockBean
    TurnstileClient turnstileClient;

    @BeforeEach
    void setUp() {
        given(turnstileClient.isEnabled()).willReturn(true);
        given(turnstileClient.verify(any(), any()))
                .willReturn(Either.right(new TurnstileVerifyResponse(true, List.of(), null, null, null, null)));
    }

    @Test
    void shouldReturn422AndCreateNoUserWhenTurnstileTokenMissing() {
        String newEmail = "web-form-no-token-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<String> response = postRegisterForm(newEmail, "Web Form User", "testPassword123", null);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(userRepo.findByEmail(Email.create(newEmail))).isEmpty();
    }

    @Test
    void shouldReturn422AndCreateNoUserWhenTurnstileVerificationFails() {
        given(turnstileClient.verify(any(), any()))
                .willReturn(Either.left(new TurnstileError.VerificationFailed(List.of("invalid-input-response"))));

        String newEmail = "web-form-failed-verify-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<String> response = postRegisterForm(newEmail, "Web Form User", "testPassword123", "some-token");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(userRepo.findByEmail(Email.create(newEmail))).isEmpty();
    }

    @Test
    void shouldRegisterAndCreateUserWhenTurnstileVerificationSucceeds() {
        String newEmail = "web-form-success-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<String> response = postRegisterForm(newEmail, "Web Form User", "testPassword123", "valid-token");

        // AuthController redirects on success rather than returning a body. The injected
        // TestRestTemplate follows that redirect (to /my-table), which then legitimately 403s on
        // its own terms (a POST there needs a CSRF token this test doesn't carry) — unrelated to
        // Turnstile and not the thing under test here. The real signal that registration actually
        // succeeded is the persisted row: never 422 (the failure status this suite cares about),
        // and a real t_user row.
        assertThat(response.getStatusCode().value()).isNotEqualTo(422);
        assertThat(userRepo.findByEmail(Email.create(newEmail))).isPresent();
    }

    @Test
    void shouldRegisterSuccessfullyWhenTurnstileDisabledEvenWithNoToken() {
        given(turnstileClient.isEnabled()).willReturn(false);

        String newEmail = "web-form-disabled-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<String> response = postRegisterForm(newEmail, "Web Form User", "testPassword123", null);

        assertThat(response.getStatusCode().value()).isNotEqualTo(422);
        assertThat(userRepo.findByEmail(Email.create(newEmail))).isPresent();
    }

    private ResponseEntity<String> postRegisterForm(
            String email, String displayName, String password, String turnstileToken) {
        String url = "http://localhost:" + port + "/auth/register";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", email);
        form.add("displayName", displayName);
        form.add("password", password);
        form.add("confirmPassword", password);
        if (turnstileToken != null) {
            form.add("cf-turnstile-response", turnstileToken);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }
}
