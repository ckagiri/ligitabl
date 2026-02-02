package com.ligitabl.api.importer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ligitabl.api.runners.importer.model.errors.ApiError;
import com.ligitabl.api.runners.importer.model.errors.DatabaseError;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ligitabl.api.runners.importer.model.entities.ImportSummary;
import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.runners.importer.ImportMatchesUseCase;

/**
 * End-to-end integration tests for match import workflow.
 *
 * Notes:
 * - Uses Testcontainers for Postgres.
 * - Uses WireMock to stub Football-Data API.
 * - Some tests are disabled until DB setup fixtures are added.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Match Import E2E Integration Tests")
class MatchImportE2EIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ligitabl_test")
            .withUsername("test")
            .withPassword("test");

    private static WireMockServer wireMock;

    @Autowired
    private ImportMatchesUseCase useCase;

    private static synchronized void ensureWireMockStarted() {
        if (wireMock == null) {
            wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        }

        if (!wireMock.isRunning()) {
            wireMock.start();
            WireMock.configureFor("localhost", wireMock.port());
        }
    }

    @AfterAll
    static void teardownWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        ensureWireMockStarted();
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        ensureWireMockStarted();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("football-data.api.url", () -> wireMock.baseUrl());
        registry.add("football-data.api.token", () -> "test-api-token");
        registry.add("football-data.api.timeout-seconds", () -> "1");
        registry.add("football-data.api.retry-attempts", () -> "0");
    }

    @Nested
    @DisplayName("Complete workflow tests")
    class CompleteWorkflow {

        @Test
        @DisplayName("Should import matches end-to-end")
        @Disabled("Requires database setup - enable after adding fixtures")
        void shouldImportMatchesEndToEnd() {
            stubFor(
                    get(urlEqualTo("/competitions/PL"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    {
                                      \"id\": 2021,
                                      \"name\": \"Premier League\",
                                      \"code\": \"PL\",
                                      \"type\": \"LEAGUE\",
                                      \"emblem\": \"https://...\",
                                      \"currentSeason\": {
                                        \"id\": 2024,
                                        \"startDate\": \"2024-08-16T00:00:00Z\",
                                        \"endDate\": \"2025-05-25T00:00:00Z\",
                                        \"currentMatchday\": 18
                                      }
                                    }
                                    """)));

            // Matches endpoint is /matches?competitions=PL&dateFrom=...&dateTo=...
            stubFor(
                    get(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/matches"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    {
                                      \"matches\": [
                                        {
                                          \"id\": 12345,
                                          \"utcDate\": \"2024-12-28T15:00:00Z\",
                                          \"status\": \"SCHEDULED\",
                                          \"matchday\": 18,
                                          \"stage\": \"REGULAR_SEASON\",
                                          \"homeTeam\": { \"id\": 57, \"name\": \"Arsenal FC\", \"tla\": \"ARS\" },
                                          \"awayTeam\": { \"id\": 61, \"name\": \"Chelsea FC\", \"tla\": \"CHE\" },
                                          \"score\": {
                                            \"winner\": null,
                                            \"duration\": \"REGULAR\",
                                            \"fullTime\": { \"home\": null, \"away\": null },
                                            \"halfTime\": { \"home\": null, \"away\": null }
                                          }
                                        }
                                      ],
                                      \"competition\": { \"id\": 2021, \"name\": \"Premier League\", \"code\": \"PL\" }
                                    }
                                    """)));

            CompetitionCode code = CompetitionCode.of("PL").get();

            Either<ImportError, ImportSummary> result = useCase.execute(code);

            assertThat(result.isRight()).isTrue();

            ImportSummary summary = result.get();
            assertThat(summary.getCompetition().getValue()).isEqualTo("PL");
            assertThat(summary.getTotalMatches()).isEqualTo(1);
            assertThat(summary.getCreated()).isEqualTo(1);
            assertThat(summary.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("Should update existing matches")
        @Disabled("Requires database setup - enable after adding fixtures")
        void shouldUpdateExistingMatches() {
            stubFor(
                    get(urlEqualTo("/competitions/PL"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    {
                                      \"id\": 2021,
                                      \"name\": \"Premier League\",
                                      \"code\": \"PL\",
                                      \"currentSeason\": {
                                        \"id\": 2024,
                                        \"startDate\": \"2024-08-16T00:00:00Z\",
                                        \"endDate\": \"2025-05-25T00:00:00Z\",
                                        \"currentMatchday\": 18
                                      }
                                    }
                                    """)));

            stubFor(
                    get(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/matches"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    {
                                      \"matches\": [
                                        {
                                          \"id\": 12345,
                                          \"utcDate\": \"2024-12-28T15:00:00Z\",
                                          \"status\": \"FINISHED\",
                                          \"matchday\": 18,
                                          \"homeTeam\": { \"id\": 57, \"name\": \"Arsenal FC\", \"tla\": \"ARS\" },
                                          \"awayTeam\": { \"id\": 61, \"name\": \"Chelsea FC\", \"tla\": \"CHE\" }
                                        }
                                      ],
                                      \"competition\": { \"id\": 2021, \"name\": \"Premier League\", \"code\": \"PL\" }
                                    }
                                    """)));

            CompetitionCode code = CompetitionCode.of("PL").get();

            Either<ImportError, ImportSummary> result = useCase.execute(code);

            assertThat(result.isRight()).isTrue();

            ImportSummary summary = result.get();
            assertThat(summary.getUpdated()).isEqualTo(1);
            assertThat(summary.getCreated()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle partial failures gracefully")
        @Disabled("Requires database setup - enable after adding fixtures")
        void shouldHandlePartialFailures() {
            stubFor(
                    get(urlEqualTo("/competitions/PL"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    {
                                      \"id\": 2021,
                                      \"name\": \"Premier League\",
                                      \"code\": \"PL\",
                                      \"currentSeason\": {
                                        \"id\": 2024,
                                        \"startDate\": \"2024-08-16T00:00:00Z\",
                                        \"endDate\": \"2025-05-25T00:00:00Z\"
                                      }
                                    }
                                    """)));

            stubFor(
                    get(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/matches"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    {
                                      \"matches\": [
                                        {
                                          \"id\": 1,
                                          \"utcDate\": \"2024-12-28T15:00:00Z\",
                                          \"status\": \"SCHEDULED\",
                                          \"matchday\": 18,
                                          \"homeTeam\": { \"id\": 57, \"name\": \"Arsenal\", \"tla\": \"ARS\" },
                                          \"awayTeam\": { \"id\": 61, \"name\": \"Chelsea\", \"tla\": \"CHE\" }
                                        },
                                        {
                                          \"id\": 2,
                                          \"utcDate\": \"2024-12-28T15:00:00Z\",
                                          \"status\": \"SCHEDULED\",
                                          \"matchday\": 18,
                                          \"homeTeam\": { \"id\": 999, \"name\": \"Unknown\", \"tla\": \"UNK\" },
                                          \"awayTeam\": { \"id\": 61, \"name\": \"Chelsea\", \"tla\": \"CHE\" }
                                        }
                                      ],
                                      \"competition\": { \"id\": 2021, \"name\": \"Premier League\", \"code\": \"PL\" }
                                    }
                                    """)));

            CompetitionCode code = CompetitionCode.of("PL").get();

            Either<ImportError, ImportSummary> result = useCase.execute(code);

            assertThat(result.isRight()).isTrue();

            ImportSummary summary = result.get();
            assertThat(summary.getTotalMatches()).isEqualTo(2);
            assertThat(summary.getSuccessCount()).isEqualTo(1);
            assertThat(summary.getFailed()).isEqualTo(1);
            assertThat(summary.isPartialSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Error scenarios")
    class ErrorScenarios {

        @Test
        @DisplayName("Should fail gracefully when season not found")
        @Disabled("Requires database setup - enable after adding fixtures")
        void shouldFailWhenSeasonNotFound() {
            stubFor(
                    get(urlEqualTo("/competitions/PL"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    {
                                      \"id\": 2021,
                                      \"name\": \"Premier League\",
                                      \"code\": \"PL\",
                                      \"currentSeason\": {
                                        \"id\": 9999,
                                        \"startDate\": \"2024-08-16T00:00:00Z\",
                                        \"endDate\": \"2025-05-25T00:00:00Z\"
                                      }
                                    }
                                    """)));

            CompetitionCode code = CompetitionCode.of("PL").get();

            Either<ImportError, ImportSummary> result = useCase.execute(code);

            assertThat(result.isLeft()).isTrue();
            ImportError error = result.getLeft();
            assertThat(error).isInstanceOf(DatabaseError.class);
            assertThat(error.message()).contains("Season");
        }

        @Test
        @DisplayName("Should fail when external API is down")
        void shouldFailWhenApiDown() {
            stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse().withStatus(503).withBody("Service temporarily unavailable")));

            CompetitionCode code = CompetitionCode.of("PL").get();

            Either<ImportError, ImportSummary> result = useCase.execute(code);

            assertThat(result.isLeft()).isTrue();
            ImportError error = result.getLeft();
            assertThat(error).isInstanceOf(ApiError.class);
        }

        @Test
        @DisplayName("Should handle rate limiting")
        void shouldHandleRateLimiting() {
            stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse()
                            .withStatus(429)
                            .withHeader("X-Requests-Available", "0")
                            .withBody("Rate limit exceeded")));

            CompetitionCode code = CompetitionCode.of("PL").get();

            Either<ImportError, ImportSummary> result = useCase.execute(code);

            assertThat(result.isLeft()).isTrue();
            ImportError error = result.getLeft();
            assertThat(error.code()).isEqualTo("API_RATE_LIMITED");
        }
    }

    @Nested
    @DisplayName("Database verification")
    class DatabaseVerification {

        @Test
        @DisplayName("Should persist match with correct relationships")
        @Disabled("Requires database setup - enable after adding fixtures")
        void shouldPersistMatchWithRelationships() {
            // Placeholder: enable after adding DB fixtures and repo verification.
        }

        @Test
        @DisplayName("Should not create duplicate matches")
        @Disabled("Requires database setup - enable after adding fixtures")
        void shouldNotCreateDuplicates() {
            // Placeholder: enable after adding DB fixtures and repo verification.
        }
    }
}
