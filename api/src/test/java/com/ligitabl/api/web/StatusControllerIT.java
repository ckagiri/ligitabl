package com.ligitabl.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import com.ligitabl.api.testsupport.AbstractPostgresIT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatusControllerIT extends AbstractPostgresIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void status_shouldReturnOk() {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response =
                (ResponseEntity) restTemplate.getForEntity("http://localhost:" + port + "/api/status", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("ok");
        assertThat(body.get("service")).isEqualTo("ligitabl");
    }
}
