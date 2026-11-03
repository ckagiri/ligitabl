package com.ligitabl.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import java.util.List;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.model.repo.SeasonRepo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Uncaught exception on a page (real servlet error dispatch)")
class UncaughtErrorPageIT extends AbstractPostgresIT {

    @Autowired
    TestRestTemplate rest;

    @SpyBean
    SeasonRepo seasonRepo;

    @Test
    @DisplayName("Renders the 500 page without the exception's message")
    void databaseFailureRendersGenericPage() {
        doThrow(new DataAccessException("SQL [select secret_column from t_season]"))
                .when(seasonRepo)
                .findActiveSeason(anyString());

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        var response = rest.exchange("/my-table/guest", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Something went wrong").doesNotContain("secret_column");
    }
}
