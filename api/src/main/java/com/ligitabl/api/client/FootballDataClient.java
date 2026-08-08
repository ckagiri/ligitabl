package com.ligitabl.api.client;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.ligitabl.api.client.footballdata.CompetitionResponse;
import com.ligitabl.api.client.footballdata.MatchDto;
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

    /**
     * The date-range window is a calendar date, so it needs a zone: {@code LocalDate.now(clock)}
     * resolves it in UTC to match the application's {@code Clock.systemUTC()} bean and the
     * timestamps this API returns. A bare {@code LocalDate.now()} used the JVM default zone, so on
     * a machine west of UTC the "today+tomorrow" request could ask for a day the upstream calendar
     * had already moved past.
     */
    private final Clock clock;

    /**
     * Get live matches for a competition
     */
    public Either<FootballDataApiError, MatchesResponse> getLiveMatches(String competitionCode) {
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
                return Either.left(new FootballDataApiError.UnexpectedError("Null response from API"));
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
    public Either<FootballDataApiError, MatchesResponse> getMatchesForDate(String competitionCode, LocalDate date) {
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
                return Either.left(new FootballDataApiError.UnexpectedError("Null response from API"));
            }

            log.debug("Fetched {} upcoming matches", response.matches().size());

            return Either.right(response);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    public Either<FootballDataApiError, MatchesResponse> getMatchesForCompetition(String competitionCode) {
        log.info("Fetching matches for competition: {}", competitionCode);

        try {
            // Optimized endpoint: /matches?competitions=PL&date=2024-12-28
            var response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/competitions/{competitionCode}/matches")
                            .build(competitionCode))
                    .retrieve()
                    .bodyToMono(MatchesResponse.class)
                    .block();

            if (response == null || response.matches() == null) {
                return Either.left(new FootballDataApiError.UnexpectedError("Null response from API"));
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
    public Either<FootballDataApiError, MatchesResponse> getMatchesInDateRange(
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
                return Either.left(new FootballDataApiError.UnexpectedError("Null response from API"));
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
    public Either<FootballDataApiError, MatchesResponse> getUpcomingMatches(String competitionCode) {
        LocalDate today = LocalDate.now(clock);
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
                return Either.left(new FootballDataApiError.UnknownError("Null response from API", null));
            }

            log.debug("Fetched {} upcoming matches", response.matches().size());

            return Either.right(response);

        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Get specific matches by their external IDs
     */
    public Either<FootballDataApiError, MatchesResponse> getMatchesByIds(
            String competitionCode, List<Integer> matchIds) {
        var ids = matchIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        log.debug("Fetching matches by ids: competition={}, ids={}", competitionCode, ids);

        try {
            var response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/matches")
                            .queryParam("competitions", competitionCode)
                            .queryParam("ids", ids)
                            .build())
                    .retrieve()
                    .bodyToMono(MatchesResponse.class)
                    .block();

            if (response == null || response.matches() == null) {
                return Either.left(new FootballDataApiError.UnexpectedError("Null response from API"));
            }

            log.debug("Fetched {} matches by ids", response.matches().size());

            return Either.right(response);

        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Get a single match by its external ID
     */
    public Either<FootballDataApiError, MatchDto> getMatchById(int matchId) {
        log.debug("Fetching match by id: {}", matchId);

        try {
            var response = webClient
                    .get()
                    .uri("/matches/{matchId}", matchId)
                    .retrieve()
                    .bodyToMono(MatchDto.class)
                    .block();

            if (response == null) {
                return Either.left(new FootballDataApiError.UnexpectedError("Null response from API"));
            }

            log.debug("Fetched match: id={}, status={}", matchId, response.status());

            return Either.right(response);

        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Get competition information including current matchday
     */
    public Either<FootballDataApiError, CompetitionResponse> getCompetition(String competitionCode) {
        log.debug("Fetching competition: code={}", competitionCode);

        try {
            var response = webClient
                    .get()
                    .uri("/competitions/{code}", competitionCode)
                    .retrieve()
                    .bodyToMono(CompetitionResponse.class)
                    .block();

            if (response == null) {
                return Either.left(new FootballDataApiError.UnknownError("Null response from API", null));
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

    private <T> Either<FootballDataApiError, T> handleException(Exception e) {
        if (e instanceof WebClientResponseException.Unauthorized unauthorized) {
            log.error("API authentication failed", e);
            return Either.left(
                    new FootballDataApiError.Unauthorized("Invalid API token: " + unauthorized.getMessage()));
        } else if (e instanceof WebClientResponseException.TooManyRequests tooMany) {
            log.warn("API rate limit exceeded", e);
            return Either.left(new FootballDataApiError.RateLimitExceeded(
                    "Rate limit exceeded: " + tooMany.getHeaders().getFirst("X-Requests-Available")));
        } else if (e instanceof WebClientResponseException.NotFound notFound) {
            log.error("Competition not found", e);
            return Either.left(new FootballDataApiError.NotFound(notFound.getMessage()));
        } else if (e instanceof WebClientResponseException webClient) {
            if (webClient.getStatusCode().is5xxServerError()) {
                log.error("API service unavailable", e);
                return Either.left(new FootballDataApiError.ServerError(
                        webClient.getMessage(), webClient.getStatusCode().value()));
            }
            return Either.left(new FootballDataApiError.UnknownError(webClient.getMessage(), webClient));
        } else {
            return Either.left(new FootballDataApiError.NetworkError("Network error: " + e.getMessage(), e));
        }
    }
}
