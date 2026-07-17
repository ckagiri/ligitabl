package com.ligitabl.api.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Post-deploy sanity check for {@link RateLimitFilter}'s per-IP keying.
 *
 * The rate limiter keys buckets by {@code request.getRemoteAddr()}, which behind the prod
 * nginx-proxy is only correct if Tomcat's RemoteIpValve is resolving X-Forwarded-For
 * (server.forward-headers-strategy=native). If that wiring ever breaks, every client resolves
 * to the nginx container's address and all users silently share one bucket.
 *
 * Hitting this endpoint from any browser shows what the app resolved for YOU:
 * your public IP means forwarding works; a private address (172.x/10.x/192.168.x) means it
 * doesn't. It only echoes the caller's own connection data, so it is safe to expose publicly;
 * the path is deliberately unguessable to keep it out of casual scanner traffic.
 */
@RestController
public class ClientIpDiagnosticController {

    static final String PATH = "/__diag/whoami-b8f31c96d2e7";

    @GetMapping(value = PATH, produces = MediaType.TEXT_PLAIN_VALUE)
    public String whoami(HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        StringBuilder body = new StringBuilder()
                .append("clientIp=").append(clientIp).append('\n')
                .append("xForwardedFor=").append(xForwardedFor).append('\n')
                .append("scheme=").append(request.getScheme()).append('\n');

        if (isPrivateAddress(clientIp)) {
            body.append("verdict=WARNING: clientIp is a private/proxy address — forwarded headers are NOT "
                    + "being applied, so all clients share one rate-limit bucket\n");
        } else {
            body.append("verdict=OK: clientIp is a public address — per-client rate limiting is keyed correctly\n");
        }
        return body.toString();
    }

    private static boolean isPrivateAddress(String ip) {
        if (ip == null) {
            return false;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.")) {
            return true;
        }
        // 172.16.0.0/12 — second octet 16..31
        if (ip.startsWith("172.")) {
            int secondDot = ip.indexOf('.', 4);
            if (secondDot > 4) {
                try {
                    int secondOctet = Integer.parseInt(ip.substring(4, secondDot));
                    return secondOctet >= 16 && secondOctet <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }
}
