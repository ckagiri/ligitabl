package com.ligitabl.api.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Post-deploy check for {@link RateLimitFilter}'s per-IP keying. Behind the prod nginx-proxy,
 * {@code getRemoteAddr()} is right only while Tomcat's RemoteIpValve resolves X-Forwarded-For
 * (server.forward-headers-strategy=native); otherwise every user shares one bucket.
 *
 * Shows what the app resolved for the caller: a public IP means forwarding works, a private
 * address means it doesn't. It echoes only the caller's own connection data, so it is safe to
 * expose; the path is unguessable to keep scanners off it.
 */
@RestController
public class ClientIpDiagnosticController {

    static final String PATH = "/__diag/whoami-b8f31c96d2e7";

    @GetMapping(value = PATH, produces = MediaType.TEXT_PLAIN_VALUE)
    public String whoami(HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        StringBuilder body = new StringBuilder()
                .append("clientIp=")
                .append(clientIp)
                .append('\n')
                .append("xForwardedFor=")
                .append(xForwardedFor)
                .append('\n')
                .append("scheme=")
                .append(request.getScheme())
                .append('\n');

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
