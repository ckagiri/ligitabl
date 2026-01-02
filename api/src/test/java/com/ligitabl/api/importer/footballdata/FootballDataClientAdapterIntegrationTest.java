package com.ligitabl.api.importer.footballdata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

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
import com.ligitabl.api.importer.model.errors.ApiError;
import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.shared.Either;
import java.util.Objects;

import org.springframework.web.reactive.function.client.WebClient;

@DisplayName("FootballDataClientAdapter Integration Tests")
class FootballDataClientAdapterIntegrationTest {

    private static WireMockServer wireMock;

    private static FootballDataClientAdapter adapter;

    @BeforeAll
    static void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        String baseUrl = Objects.requireNonNull(wireMock.baseUrl(), "wiremock baseUrl");

        WebClient webClient = WebClient.builder()
            .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", "test-token")
                .defaultHeader("Accept", "application/json")
                .build();

        adapter = new FootballDataClientAdapter(new FootballDataClient(webClient));
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
            LocalDate today = LocalDate.now();
            LocalDate dayAfterTomorrow = today.plusDays(2);

            wireMock.stubFor(get(urlPathEqualTo("/matches"))
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
                                        }
                                      ]
                                    }
                                    """)));

            var code = CompetitionCode.of("PL").get();

            var result = adapter.fetchMatches(code);

            assertThat(result.isRight()).isTrue();
            var matches = result.get();
            assertThat(matches).hasSize(1);
            assertThat(matches.get(0).getId().getValue()).isEqualTo(12345);
            assertThat(matches.get(0).getHomeTeam().getId().getValue()).isEqualTo(57);
            assertThat(matches.get(0).getAwayTeam().getId().getValue()).isEqualTo(61);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/matches"))
                    .withQueryParam("competitions", equalTo("PL"))
                    .withQueryParam("dateFrom", equalTo(today.toString()))
                    .withQueryParam("dateTo", equalTo(dayAfterTomorrow.toString()))
                    .withHeader("X-Auth-Token", equalTo("test-token")));
        }
    }
}
