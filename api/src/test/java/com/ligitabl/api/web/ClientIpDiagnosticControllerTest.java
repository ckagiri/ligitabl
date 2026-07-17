package com.ligitabl.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link ClientIpDiagnosticController}.
 *
 * Plain unit tests — no Spring context needed.
 */
class ClientIpDiagnosticControllerTest {

    private final ClientIpDiagnosticController controller = new ClientIpDiagnosticController();

    @Test
    @DisplayName("Public client IP reports OK verdict")
    void publicIp_reportsOk() {
        var request = new MockHttpServletRequest("GET", ClientIpDiagnosticController.PATH);
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        String body = controller.whoami(request);

        assertThat(body).contains("clientIp=203.0.113.7");
        assertThat(body).contains("xForwardedFor=203.0.113.7");
        assertThat(body).contains("verdict=OK");
    }

    @Test
    @DisplayName("Docker-network client IP reports WARNING (forwarded headers not applied)")
    void dockerNetworkIp_reportsWarning() {
        var request = new MockHttpServletRequest("GET", ClientIpDiagnosticController.PATH);
        request.setRemoteAddr("172.18.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        String body = controller.whoami(request);

        assertThat(body).contains("clientIp=172.18.0.2");
        assertThat(body).contains("verdict=WARNING");
    }

    @Test
    @DisplayName("172.x outside the 172.16/12 private range is treated as public")
    void publicIpIn172Space_reportsOk() {
        var request = new MockHttpServletRequest("GET", ClientIpDiagnosticController.PATH);
        request.setRemoteAddr("172.64.10.5");

        String body = controller.whoami(request);

        assertThat(body).contains("verdict=OK");
    }
}
