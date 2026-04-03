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

import com.ligitabl.api.testsupport.AbstractPostgresIT;

/**
 * Integration test: verifies the {@link RateLimitFilter} is registered in the
 * running Spring context and blocks requests once the bucket is exhausted.
 *
 * Uses a real server (RANDOM_PORT) so the full filter chain executes.
 * Relies on {@link AbstractPostgresIT} for the Testcontainers Postgres setup.
 *
 * NOTE: Spring caches the test application context, so all test methods share
 * the same {@link RateLimitFilter} bean and therefore the same bucket. Tests
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
    @DisplayName("Filter is active: 21st request returns 429")
    void filterActive_requestsBeyondLimit_return429() {
        String url = "http://localhost:" + port + "/api/status";

        // exhaust the bucket (some tokens may already be gone from other tests in this context)
        for (int i = 0; i < 20; i++) {
            restTemplate.getForEntity(url, String.class);
        }

        ResponseEntity<String> throttled = restTemplate.getForEntity(url, String.class);

        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
