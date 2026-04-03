package com.ligitabl.api.web;

import java.io.IOException;
import java.time.Duration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;

/**
 * Global rate limiting filter using Bucket4j (token bucket algorithm).
 *
 * A single shared bucket allows 20 requests per minute across all clients.
 * Requests beyond the limit receive a 429 response immediately.
 *
 * Actuator health checks are exempt to avoid interfering with monitoring.
 *
 * Ordering: runs after {@link RequestLoggingFilter} (+10) so that 429 responses
 * are still captured in the request log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int REQUESTS_PER_MINUTE = 20;

    private final Bucket bucket;

    public RateLimitFilter() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(REQUESTS_PER_MINUTE)
                .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                .build();
        this.bucket = Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many requests. Please try again later.\"}");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/actuator/health");
    }
}
