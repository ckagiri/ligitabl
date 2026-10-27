package com.ligitabl.api.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ligitabl.api.client.footballdata.MatchDto;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

@DisplayName("FootballDataClient over HTTP")
class FootballDataClientTest {

    private static WireMockServer wireMock;

    private static FootballDataClient client;

    @BeforeAll
    static void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        client = createClient(wireMock.baseUrl(), Duration.ofSeconds(10));
    }

    private static FootballDataClient createClient(String baseUrl, Duration timeout) {
        String resolvedBaseUrl = Objects.requireNonNull(baseUrl, "baseUrl");

        HttpClient httpClient = HttpClient.create().responseTimeout(timeout).doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS)));

        WebClient webClient = WebClient.builder()
                .baseUrl(resolvedBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-Auth-Token", "test-token")
                .defaultHeader("Accept", "application/json")
                .build();

        return new FootballDataClient(webClient, Clock.systemUTC());
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
            wireMock.stubFor(
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
                                      \"currentSeason\": {
                                        \"id\": 2024,
                                        \"startDate\": \"2024-08-16T00:00:00Z\",
                                        \"endDate\": \"2025-05-25T00:00:00Z\",
                                        \"currentMatchday\": 18
                                      }
                                    }
                                    """)));

            var result = client.getCompetition("PL");

            assertThat(result.isRight()).isTrue();
            var competition = result.get();
            assertThat(competition.id()).isEqualTo(2021);
            assertThat(competition.name()).isEqualTo("Premier League");
            assertThat(competition.code()).isEqualTo("PL");
            assertThat(competition.currentSeason().id()).isEqualTo(2024);

            wireMock.verify(
                    getRequestedFor(urlEqualTo("/competitions/PL")).withHeader("X-Auth-Token", equalTo("test-token")));
        }

        @Test
        @DisplayName("should handle 404")
        void shouldHandle404() {
            wireMock.stubFor(get(urlEqualTo("/competitions/XX"))
                    .willReturn(aResponse().withStatus(404).withBody("Not found")));

            var result = client.getCompetition("XX");

            assertThat(result.getLeft()).isInstanceOf(FootballDataApiError.NotFound.class);
        }

        @Test
        @DisplayName("should map 429 to RateLimitExceeded")
        void shouldHandleRateLimit() {
            wireMock.stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse().withStatus(429).withBody("Rate limit")));

            var result = client.getCompetition("PL");

            assertThat(result.getLeft()).isInstanceOf(FootballDataApiError.RateLimitExceeded.class);
        }

        @Test
        @DisplayName("should handle 500")
        void shouldHandle500() {
            wireMock.stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse().withStatus(500).withBody("Internal server error")));

            var result = client.getCompetition("PL");

            assertThat(result.getLeft())
                    .isInstanceOfSatisfying(FootballDataApiError.ServerError.class, e -> assertThat(e.statusCode())
                            .isEqualTo(500));
        }

        @Test
        @DisplayName("should handle malformed JSON")
        void shouldHandleMalformedJson() {
            wireMock.stubFor(get(urlEqualTo("/competitions/PL"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{ invalid json }")));

            var result = client.getCompetition("PL");

            assertThat(result.isLeft()).isTrue();
        }
    }

    @Nested
    @DisplayName("fetchMatches")
    class FetchMatches {

        @Test
        @DisplayName("should fetch matches successfully")
        void shouldFetchMatches() {
            wireMock.stubFor(
                    get(urlEqualTo("/competitions/PL/matches"))
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
                                          \"homeTeam\": { \"id\": 57, \"name\": \"Arsenal FC\", \"tla\": \"ARS\" },
                                          \"awayTeam\": { \"id\": 61, \"name\": \"Chelsea FC\", \"tla\": \"CHE\" }
                                                                                    ,\"score\": { \"fullTime\": { \"home\": 1, \"away\": 2 } }
                                        }
                                      ]
                                    }
                                    """)));

            var result = client.getMatchesForCompetition("PL");

            assertThat(result.isRight()).isTrue();
            var matches = result.get().matches();
            assertThat(matches).hasSize(1);
            MatchDto match = matches.get(0);
            assertThat(match.id()).isEqualTo(12345);
            assertThat(match.homeTeam().id()).isEqualTo(57);
            assertThat(match.awayTeam().id()).isEqualTo(61);
            assertThat(match.score()).isNotNull();
            assertThat(match.score().fullTime().home()).isEqualTo(1);
            assertThat(match.score().fullTime().away()).isEqualTo(2);

            wireMock.verify(getRequestedFor(urlEqualTo("/competitions/PL/matches"))
                    .withHeader("X-Auth-Token", equalTo("test-token")));
        }

        @Test
        @DisplayName("should handle empty matches list")
        void shouldHandleEmptyMatches() {
            wireMock.stubFor(
                    get(urlEqualTo("/competitions/PL/matches"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                                                        { "matches": [] }
                                                                        """)));

            var result = client.getMatchesForCompetition("PL");

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().matches()).isEmpty();
        }

        @Test
        @DisplayName("should handle multiple matches")
        void shouldHandleMultipleMatches() {
            wireMock.stubFor(
                    get(urlEqualTo("/competitions/PL/matches"))
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
                                                                                    \"utcDate\": \"2024-12-28T17:30:00Z\",
                                                                                    \"status\": \"SCHEDULED\",
                                                                                    \"matchday\": 18,
                                                                                    \"homeTeam\": { \"id\": 65, \"name\": \"Manchester City\", \"tla\": \"MCI\" },
                                                                                    \"awayTeam\": { \"id\": 66, \"name\": \"Manchester United\", \"tla\": \"MUN\" }
                                                                                }
                                                                            ]
                                                                        }
                                                                        """)));

            var result = client.getMatchesForCompetition("PL");

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().matches()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle connection timeout")
        void shouldHandleTimeout() {
            FootballDataClient timeoutClient = createClient(wireMock.baseUrl(), Duration.ofSeconds(1));

            wireMock.stubFor(
                    get(urlEqualTo("/competitions/PL")).willReturn(aResponse().withFixedDelay(31000)));

            var result = timeoutClient.getCompetition("PL");

            assertThat(result.getLeft()).isInstanceOf(FootballDataApiError.NetworkError.class);
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

            FootballDataClient refusedClient = createClient("http://localhost:" + unusedPort, Duration.ofSeconds(1));

            var result = refusedClient.getCompetition("PL");

            assertThat(result.getLeft()).isInstanceOf(FootballDataApiError.NetworkError.class);
        }
    }
}
