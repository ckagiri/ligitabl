package com.ligitabl.api.importer.footballdata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.importer.model.entities.ExternalCompetition;
import com.ligitabl.api.importer.model.entities.ExternalMatch;
import com.ligitabl.api.importer.model.errors.ApiError;
import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.shared.Either;

import org.springframework.web.reactive.function.client.WebClient;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

@DisplayName("FootballDataClientAdapter Integration Tests")
class FootballDataClientAdapterIntegrationTest {

    private static WireMockServer wireMock;

    private static FootballDataClientAdapter adapter;

    @BeforeAll
    static void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        adapter = createAdapter(wireMock.baseUrl(), Duration.ofSeconds(10));
    }

    private static FootballDataClientAdapter createAdapter(String baseUrl, Duration timeout) {
        String resolvedBaseUrl = Objects.requireNonNull(baseUrl, "baseUrl");

        HttpClient httpClient = HttpClient.create()
            .responseTimeout(timeout)
            .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS)));

        WebClient webClient = WebClient.builder()
            .baseUrl(resolvedBaseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("X-Auth-Token", "test-token")
            .defaultHeader("Accept", "application/json")
            .build();

        return new FootballDataClientAdapter(new FootballDataClient(webClient));
    }

    @AfterAll
    static void teardown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    @Nested
    @DisplayName("fetchCompetition")
    class FetchCompetition {

        @Test
        @DisplayName("should fetch competition successfully")
        void shouldFetchCompetition() {
            wireMock.stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                    """
                                    {
                                      \"id\": 2021,
                                      \"name\": \"Premier League\",
                                      \"code\": \"PL\",
                                      \"type\": \"LEAGUE\",
                                      \"currentSeason\": {
                                        \"id\": 2024,
                                        \"startDate\": \"2024-08-16T00:00:00Z\",
                                        \"endDate\": \"2025-05-25T00:00:00Z\",
                                        \"currentMatchday\": 18
                                      }
                                    }
                                    """)));

            var code = CompetitionCode.of("PL").get();

            Either<ImportError, ?> result = adapter.fetchCompetition(code);

            assertThat(result.isRight()).isTrue();
            var competition = (ExternalCompetition) result.get();
            assertThat(competition.getId().getValue()).isEqualTo(2021);
            assertThat(competition.getName()).isEqualTo("Premier League");
            assertThat(competition.getCode().getValue()).isEqualTo("PL");
            assertThat(competition.getCurrentSeason().getId().getValue()).isEqualTo(2024);

            wireMock.verify(getRequestedFor(urlEqualTo("/competitions/PL"))
                    .withHeader("X-Auth-Token", equalTo("test-token")));
        }

        @Test
        @DisplayName("should handle 404")
        void shouldHandle404() {
            wireMock.stubFor(get(urlEqualTo("/competitions/XX"))
                    .willReturn(aResponse().withStatus(404).withBody("Not found")));

            var code = CompetitionCode.of("XX").get();

            var result = adapter.fetchCompetition(code);

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(ApiError.class);
            assertThat(((ApiError) result.getLeft()).getStatusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("should map rate limit to API_RATE_LIMITED")
        void shouldHandleRateLimit() {
            wireMock.stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse().withStatus(429).withBody("Rate limit")));

            var code = CompetitionCode.of("PL").get();

            var result = adapter.fetchCompetition(code);

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(ApiError.class);
            assertThat(result.getLeft().code()).isEqualTo("API_RATE_LIMITED");
        }

        @Test
        @DisplayName("should handle 500")
        void shouldHandle500() {
            wireMock.stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse().withStatus(500).withBody("Internal server error")));

            var code = CompetitionCode.of("PL").get();

            var result = adapter.fetchCompetition(code);

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(ApiError.class);
            assertThat(((ApiError) result.getLeft()).getStatusCode()).isEqualTo(500);
        }

        @Test
        @DisplayName("should handle malformed JSON")
        void shouldHandleMalformedJson() {
            wireMock.stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                            .withBody("{ invalid json }")));

            var code = CompetitionCode.of("PL").get();

            var result = adapter.fetchCompetition(code);

            assertThat(result.isLeft()).isTrue();
        }
    }

    @Nested
    @DisplayName("fetchMatches")
    class FetchMatches {

        @Test
        @DisplayName("should fetch matches successfully")
        void shouldFetchMatches() {
            wireMock.stubFor(get(urlEqualTo("/competitions/PL/matches"))
                    .willReturn(aResponse()
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
                                          \"homeTeam\": { \"id\": 57, \"name\": \"Arsenal FC\", \"tla\": \"ARS\" },
                                          \"awayTeam\": { \"id\": 61, \"name\": \"Chelsea FC\", \"tla\": \"CHE\" }
                                                                                    ,\"score\": { \"fullTime\": { \"home\": 1, \"away\": 2 } }
                                        }
                                      ]
                                    }
                                    """)));

            var code = CompetitionCode.of("PL").get();

            var result = adapter.fetchMatchesForCompetition(code);

            assertThat(result.isRight()).isTrue();
            var matches = result.get();
            assertThat(matches).hasSize(1);
            ExternalMatch match = matches.get(0);
            assertThat(match.getId().getValue()).isEqualTo(12345);
            assertThat(match.getHomeTeam().getId().getValue()).isEqualTo(57);
            assertThat(match.getAwayTeam().getId().getValue()).isEqualTo(61);
            assertThat(match.getScore()).isPresent();
            assertThat(match.getScore().get().homeGoals()).isEqualTo(1);
            assertThat(match.getScore().get().awayGoals()).isEqualTo(2);

                wireMock.verify(getRequestedFor(urlEqualTo("/competitions/PL/matches"))
                    .withHeader("X-Auth-Token", equalTo("test-token")));
        }

                @Test
                @DisplayName("should handle empty matches list")
                void shouldHandleEmptyMatches() {
                    wireMock.stubFor(get(urlEqualTo("/competitions/PL/matches"))
                                        .willReturn(aResponse()
                                                        .withStatus(200)
                                                        .withHeader("Content-Type", "application/json")
                                                        .withBody(
                                                                        """
                                                                        { "matches": [] }
                                                                        """)));

                        var code = CompetitionCode.of("PL").get();

                        Either<ImportError, List<ExternalMatch>> result = adapter.fetchMatchesForCompetition(code);

                        assertThat(result.isRight()).isTrue();
                        assertThat(result.get()).isEmpty();
                }

                @Test
                @DisplayName("should handle multiple matches")
                void shouldHandleMultipleMatches() {
                    wireMock.stubFor(get(urlEqualTo("/competitions/PL/matches"))
                                        .willReturn(aResponse()
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
                                                                                    \"utcDate\": \"2024-12-28T17:30:00Z\",
                                                                                    \"status\": \"SCHEDULED\",
                                                                                    \"matchday\": 18,
                                                                                    \"homeTeam\": { \"id\": 65, \"name\": \"Manchester City\", \"tla\": \"MCI\" },
                                                                                    \"awayTeam\": { \"id\": 66, \"name\": \"Manchester United\", \"tla\": \"MUN\" }
                                                                                }
                                                                            ]
                                                                        }
                                                                        """)));

                        var code = CompetitionCode.of("PL").get();

                        Either<ImportError, List<ExternalMatch>> result = adapter.fetchMatchesForCompetition(code);

                        assertThat(result.isRight()).isTrue();
                        assertThat(result.get()).hasSize(2);
                }
        }

        @Nested
        @DisplayName("error handling")
        class ErrorHandling {

                @Test
                @DisplayName("should handle connection timeout")
                void shouldHandleTimeout() {
                        // Re-create adapter with a short timeout
                    FootballDataClientAdapter timeoutAdapter = createAdapter(wireMock.baseUrl(), Duration.ofSeconds(1));

                        wireMock.stubFor(get(urlEqualTo("/competitions/PL"))
                                        .willReturn(aResponse().withFixedDelay(31000)));

                        var code = CompetitionCode.of("PL").get();

                        var result = timeoutAdapter.fetchCompetition(code);

                        assertThat(result.isLeft()).isTrue();
                        assertThat(result.getLeft()).isInstanceOf(ApiError.class);
                }

                @Test
                @DisplayName("should handle connection refused")
                void shouldHandleConnectionRefused() {
                    int unusedPort;
                    try (ServerSocket serverSocket = new ServerSocket(0)) {
                        unusedPort = serverSocket.getLocalPort();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to allocate an unused port", e);
                    }

                    FootballDataClientAdapter refusedAdapter = createAdapter(
                            "http://localhost:" + unusedPort,
                            Duration.ofSeconds(1));

                    var code = CompetitionCode.of("PL").get();

                    var result = refusedAdapter.fetchCompetition(code);

                        assertThat(result.isLeft()).isTrue();
                        assertThat(result.getLeft()).isInstanceOf(ApiError.class);
                    assertThat(result.getLeft().code()).isEqualTo("API_CONNECTION_FAILED");
                }
    }
}
