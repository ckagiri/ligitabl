package com.ligitabl.api.web;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ligitabl.api.logging.RequestId;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs inbound requests with method, path, status and duration, never bodies (PII). Propagates
 * X-Request-Id (or generates one) as MDC "requestId" and on the response; health checks log at DEBUG.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String requestId = RequestId.resolve(request.getHeader(RequestId.HEADER));
        MDC.put(RequestId.MDC_KEY, requestId);
        response.setHeader(RequestId.HEADER, requestId);

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

            MDC.remove(RequestId.MDC_KEY);
        }
    }

    private boolean isHealth(String uri) {
        return uri != null && (uri.equals("/actuator/health") || uri.startsWith("/actuator/health"));
    }

    private String safeHeader(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return v == null ? "" : v;
    }
}
