package com.ligitabl.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link RateLimitFilter}.
 *
 * Uses Spring's mock servlet objects — no Spring context needed.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    @DisplayName("Requests within the limit pass through with 200")
    void requestsWithinLimit_passThrough() throws Exception {
        for (int i = 0; i < 20; i++) {
            var request = new MockHttpServletRequest("GET", "/api/status");
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus())
                    .as("request %d should pass through", i + 1)
                    .isNotEqualTo(429);
            assertThat(chain.getRequest())
                    .as("filter chain should have been invoked for request %d", i + 1)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("21st request in the same minute returns 429")
    void requestBeyondLimit_returns429() throws Exception {
        // exhaust the bucket
        for (int i = 0; i < 20; i++) {
            var chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", "/api/status"), new MockHttpServletResponse(), chain);
        }

        var request = new MockHttpServletRequest("GET", "/api/status");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest())
                .as("filter chain should NOT have been invoked")
                .isNull();
    }

    @Test
    @DisplayName("429 response body contains a message")
    void rateLimited_responseBodyContainsMessage() throws Exception {
        for (int i = 0; i < 20; i++) {
            filter.doFilter(
                    new MockHttpServletRequest("GET", "/api/status"),
                    new MockHttpServletResponse(),
                    new MockFilterChain());
        }

        var request = new MockHttpServletRequest("GET", "/api/status");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getContentAsString()).contains("message");
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    @DisplayName("Actuator health check is exempt from rate limiting")
    void actuatorHealth_isExempt() throws Exception {
        // exhaust the bucket first
        for (int i = 0; i < 20; i++) {
            filter.doFilter(
                    new MockHttpServletRequest("GET", "/api/status"),
                    new MockHttpServletResponse(),
                    new MockFilterChain());
        }

        // health check should still pass through even though bucket is empty
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
        assertThat(chain.getRequest())
                .as("health check should bypass the filter entirely")
                .isNotNull();
    }
}
