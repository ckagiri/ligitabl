package com.ligitabl.api.web;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-client rate limiting filter using Bucket4j (token bucket algorithm).
 *
 * Each client IP gets its own bucket allowing the configured number of requests per minute,
 * so one abusive client can't exhaust the budget for everyone else. Requests beyond the
 * limit receive a 429 response immediately.
 *
 * Buckets live in a bounded Caffeine cache rather than a plain map: an unbounded map keyed
 * by remote IP is a memory-DoS vector (scanners rotating source addresses grow it forever).
 *
 * In prod the real client IP is resolved from X-Forwarded-For by Tomcat's RemoteIpValve
 * (server.forward-headers-strategy=native in application-prod.yml) — without it every request
 * would arrive from the nginx container's IP and all clients would share one bucket.
 *
 * Actuator health checks are exempt to avoid interfering with monitoring.
 *
 * Ordering: runs after {@link RequestLoggingFilter} (+10) so that 429 responses are still
 * captured in the request log. This is far before the Spring Security chain, so no
 * authenticated principal is available — IP is the only viable key at this position.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Known bot/scanner paths that will never succeed on a Spring Boot app.
    // Silently dropped: no logging, no rate-limit token consumed.
    private static final List<String> BOT_PROBE_PREFIXES = List.of(
            "/wp-admin", "/wp-login", "/wp-config", "/phpmyadmin", "/xmlrpc.php", "/.env", "/.git", "/.DS_Store");

    // Hard bound on tracked clients — worst case a few MB of heap regardless of traffic.
    private static final long MAX_TRACKED_CLIENTS = 10_000;

    // Must stay >= the refill window (1 min): a bucket idle that long is full again anyway,
    // so eviction never grants tokens the client wasn't already entitled to. A shorter expiry
    // would let clients reset a drained bucket by pausing.
    private static final Duration IDLE_CLIENT_EXPIRY = Duration.ofMinutes(10);

    private final Cache<String, Bucket> buckets;
    private final Bandwidth limit;

    // Configurable per environment (RATE_LIMIT_REQUESTS_PER_MINUTE) — dev/test can set this high
    // to avoid tripping the limiter during manual testing without touching prod's default.
    public RateLimitFilter(@Value("${ligitabl.rate-limit.requests-per-minute:120}") int requestsPerMinute) {
        this.limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_CLIENTS)
                .expireAfterAccess(IDLE_CLIENT_EXPIRY)
                .build();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (BOT_PROBE_PREFIXES.stream().anyMatch(uri::startsWith)) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        String clientIp = request.getRemoteAddr();
        Bucket bucket =
                buckets.get(clientIp, ip -> Bucket.builder().addLimit(limit).build());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for {}: {} {}", clientIp, request.getMethod(), request.getRequestURI());
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
