package com.ligitabl.api.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs inbound HTTP requests with method, path, status and duration.
 *
 * - Adds/propagates a correlation id from X-Request-Id header (or generates one)
 *   and exposes it via MDC as "requestId".
 * - Skips noisy INFO logs for health checks by logging them at DEBUG level.
 * - Does not log request/response bodies to avoid leaking PII.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String requestId = resolveOrCreateRequestId(request);
        MDC.put(MDC_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String userAgent = safeHeader(request, "User-Agent");

            if (isHealth(uri)) {
                log.debug("{} {} -> {} ({} ms) ua='{}'", method, uri, status, duration, userAgent);
            } else {
                log.info("{} {} -> {} ({} ms) ua='{}'", method, uri, status, duration, userAgent);
            }

            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private String resolveOrCreateRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private boolean isHealth(String uri) {
        return uri != null && (uri.equals("/actuator/health") || uri.startsWith("/actuator/health"));
    }

    private String safeHeader(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return v == null ? "" : v;
    }
}
