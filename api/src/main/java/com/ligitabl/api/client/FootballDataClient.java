package com.ligitabl.api.client;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.ligitabl.api.client.footballdata.CompetitionResponse;
import com.ligitabl.api.client.footballdata.MatchesResponse;
import com.ligitabl.api.shared.Either;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Football Data API Client
 *
 * Wrapper around WebClient for Football Data API v4.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FootballDataClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WebClient webClient;

    public sealed interface ApiError {
        record NetworkError(String message, Throwable cause) implements ApiError {}

        record RateLimitExceeded(String message) implements ApiError {}

        record NotFound(String message) implements ApiError {}

        record Unauthorized(String message) implements ApiError {}

        record ServerError(String message, int statusCode) implements ApiError {}

        record UnknownError(String message, Throwable cause) implements ApiError {}

        record UnexpectedError(String message) implements ApiError {}
    }

    /**
     * Get live matches for a competition
     */
    public Either<ApiError, MatchesResponse> getLiveMatches(String competitionCode) {
        log.debug("Fetching live matches: competition={}", competitionCode);

        try {
            var response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/matches")
                            .queryParam("competitions", competitionCode)
                            .queryParam("status", "LIVE")
                            .build())
                    .retrieve()
                    .bodyToMono(MatchesResponse.class)
                    .block();

            if (response == null || response.matches() == null) {
                return Either.left(new ApiError.UnexpectedError("Null response from API"));
            }

            log.debug("Fetched {} live matches", response.matches().size());

            return Either.right(response);

        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Get matches for a specific date
     * Used when: Checking imminent/soon kickoffs (today only)
     * Endpoint: GET /matches?competitions={code}&date={date}
     */
    public Either<ApiError, MatchesResponse> getMatchesForDate(String competitionCode, LocalDate date) {

        log.debug("Fetching matches for date: competition={}, date={}", competitionCode, date);

        try {
            // Optimized endpoint: /matches?competitions=PL&date=2024-12-28
            var response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/matches")
                            .queryParam("competitions", competitionCode)
                            .queryParam("date", date.format(DATE_FORMATTER))
                            .build())
                    .retrieve()
                    .bodyToMono(MatchesResponse.class)
                    .block();

            if (response == null || response.matches() == null) {
                return Either.left(new ApiError.UnexpectedError("Null response from API"));
            }

            log.debug("Fetched {} upcoming matches", response.matches().size());

            return Either.right(response);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    public Either<ApiError, MatchesResponse> getMatchesForCompetition(String competitionCode) {
        URI uri = UriComponentsBuilder.fromPath("/competitions/{competitionCode}/matches")
                .build(competitionCode);
        log.info("Fetching matches for competition: {}", uri);

        try {
            // Optimized endpoint: /matches?competitions=PL&date=2024-12-28
            var response = webClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(MatchesResponse.class)
                    .block();

            if (response == null || response.matches() == null) {
                return Either.left(new ApiError.UnexpectedError("Null response from API"));
            }

            log.debug("Fetched {} competition matches", response.matches().size());

            return Either.right(response);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Get matches within date range
     * Used when: Looking ahead (today + tomorrow for default check)
     * Endpoint: GET /matches?competitions={code}&dateFrom={from}&dateTo={to}
     */
    public Either<ApiError, MatchesResponse> getMatchesInDateRange(
            String competitionCode, LocalDate dateFrom, LocalDate dateTo) {
        log.debug("Fetching matches in range: competition={}, from={}, to={}", competitionCode, dateFrom, dateTo);

        try {
            // Optimized endpoint: /matches?competitions=PL&dateFrom=X&dateTo=Y
            var response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/matches")
                            .queryParam("competitions", competitionCode)
                            .queryParam("dateFrom", dateFrom.format(DATE_FORMATTER))
                            .queryParam("dateTo", dateTo.format(DATE_FORMATTER))
                            .build())
                    .retrieve()
                    .bodyToMono(MatchesResponse.class)
                    .block();

            if (response == null || response.matches() == null) {
                return Either.left(new ApiError.UnexpectedError("Null response from API"));
            }
            log.debug("Fetched {} upcoming matches", response.matches().size());

            return Either.right(response);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Get matches for today and tomorrow
     */
    public Either<ApiError, MatchesResponse> getUpcomingMatches(String competitionCode) {
        LocalDate today = LocalDate.now();
        LocalDate dayAfterTomorrow = today.plusDays(2);

        log.debug(
                "Fetching upcoming matches: competition={}, dateFrom={}, dateTo={}",
                competitionCode,
                today,
                dayAfterTomorrow);

        try {
            var response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/matches")
                            .queryParam("competitions", competitionCode)
                            .queryParam("dateFrom", today.format(DATE_FORMATTER))
                            .queryParam("dateTo", dayAfterTomorrow.format(DATE_FORMATTER))
                            .build())
                    .retrieve()
                    .bodyToMono(MatchesResponse.class)
                    .block();

            if (response == null) {
                return Either.left(new ApiError.UnknownError("Null response from API", null));
            }

            log.debug("Fetched {} upcoming matches", response.matches().size());

            return Either.right(response);

        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Get competition information including current matchday
     */
    public Either<ApiError, CompetitionResponse> getCompetition(String competitionCode) {
        log.debug("Fetching competition: code={}", competitionCode);

        try {
            var response = webClient
                    .get()
                    .uri("/competitions/{code}", competitionCode)
                    .retrieve()
                    .bodyToMono(CompetitionResponse.class)
                    .block();

            if (response == null) {
                return Either.left(new ApiError.UnknownError("Null response from API", null));
            }

            log.debug(
                    "Fetched competition: name={}, currentMatchday={}",
                    response.name(),
                    response.currentSeason().currentMatchday());

            return Either.right(response);

        } catch (Exception e) {
            return handleException(e);
        }
    }

    private <T> Either<ApiError, T> handleException(Exception e) {
        if (e instanceof WebClientResponseException.Unauthorized unauthorized) {
            log.error("API authentication failed", e);
            return Either.left(new ApiError.Unauthorized("Invalid API token: " + unauthorized.getMessage()));
        } else if (e instanceof WebClientResponseException.TooManyRequests tooMany) {
            log.warn("API rate limit exceeded", e);
            return Either.left(new ApiError.RateLimitExceeded(
                    "Rate limit exceeded: " + tooMany.getHeaders().getFirst("X-Requests-Available")));
        } else if (e instanceof WebClientResponseException.NotFound notFound) {
            log.error("Competition not found", e);
            return Either.left(new ApiError.NotFound(notFound.getMessage()));
        } else if (e instanceof WebClientResponseException webClient) {
            if (webClient.getStatusCode().is5xxServerError()) {
                log.error("API service unavailable", e);
                return Either.left(new ApiError.ServerError(
                        webClient.getMessage(), webClient.getStatusCode().value()));
            }
            return Either.left(new ApiError.UnknownError(webClient.getMessage(), webClient));
        } else {
            return Either.left(new ApiError.NetworkError("Network error: " + e.getMessage(), e));
        }
    }
}
