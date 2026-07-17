package com.ligitabl.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ligitabl.api.testsupport.AbstractPostgresIT;

/**
 * Integration test: verifies the {@link RateLimitFilter} is registered in the
 * running Spring context and blocks requests once the bucket is exhausted.
 *
 * Uses a real server (RANDOM_PORT) so the full filter chain executes.
 * Relies on {@link AbstractPostgresIT} for the Testcontainers Postgres setup.
 *
 * Pins the configured limit to {@link #LIMIT} via {@link DynamicPropertySource} rather than
 * relying on the ambient RATE_LIMIT_REQUESTS_PER_MINUTE env var (dev/test envs commonly set this
 * very high to disable throttling during manual testing, which would make this test impractically
 * slow or flaky otherwise).
 *
 * NOTE: Spring caches the test application context, so all test methods share
 * the same {@link RateLimitFilter} bean — and since every request originates
 * from the same loopback address, the same per-IP bucket. Tests
 * that exhaust the bucket must run in isolation — see {@link DirtiesContext} if
 * that becomes necessary. Currently the two tests here are ordered so that the
 * within-limit check runs first.
 *
 * The actuator health exemption is tested at the unit level in
 * {@link RateLimitFilterTest} where there is no redirect interference from
 * Spring Security.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitFilterIT extends AbstractPostgresIT {

    private static final int LIMIT = 20;

    @DynamicPropertySource
    static void rateLimit(DynamicPropertyRegistry registry) {
        registry.add("ligitabl.rate-limit.requests-per-minute", () -> LIMIT);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @DisplayName("Filter is active: requests within limit return non-429")
    void filterActive_requestsWithinLimit_succeed() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/api/status", String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Filter is active: request beyond limit returns 429")
    void filterActive_requestsBeyondLimit_return429() {
        String url = "http://localhost:" + port + "/api/status";

        // exhaust the bucket (some tokens may already be gone from other tests in this context)
        for (int i = 0; i < LIMIT; i++) {
            restTemplate.getForEntity(url, String.class);
        }

        ResponseEntity<String> throttled = restTemplate.getForEntity(url, String.class);

        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
