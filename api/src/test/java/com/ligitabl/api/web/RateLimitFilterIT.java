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
 * {@link RateLimitFilter} is registered in the running context and blocks requests once the bucket
 * is exhausted, through the full filter chain on a real server.
 *
 * Pins the limit to {@link #LIMIT} rather than the ambient RATE_LIMIT_REQUESTS_PER_MINUTE, which
 * dev and test envs often set very high.
 *
 * ⚠️ The cached context shares one filter bean, and every request comes from loopback, so all tests
 * share one bucket: the within-limit test is ordered first. A new test that exhausts the bucket
 * must run in isolation (see {@link DirtiesContext}). The actuator exemption is covered in
 * {@link RateLimitFilterTest}, clear of Spring Security's redirects.
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
