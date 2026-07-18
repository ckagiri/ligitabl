package com.ligitabl.api.runners.importer.footballdata;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ligitabl.api.client.FootballDataApiError;
import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.client.footballdata.CompetitionResponse;
import com.ligitabl.api.client.footballdata.MatchDto;
import com.ligitabl.api.runners.importer.model.errors.ApiError;
import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.shared.Either;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FootballDataClientAdapter implements FootballDataGateway {

    private final FootballDataClient client;

    @Override
    public Either<ImportError, CompetitionResponse> fetchCompetition(CompetitionCode code) {
        log.debug("Fetching competition via adapter: {}", code.getValue());

        return client.getCompetition(code.getValue()).mapLeft(this::mapApiError);
    }

    @Override
    public Either<ImportError, List<MatchDto>> fetchMatchesForCompetition(CompetitionCode code) {
        log.debug("Fetching matches via adapter: {}", code.getValue());

        return client.getMatchesForCompetition(code.getValue())
                .mapLeft(this::mapApiError)
                .map(response -> response.matches() == null ? List.<MatchDto>of() : response.matches());
    }

    private ImportError mapApiError(FootballDataApiError error) {
        return switch (error) {
            case FootballDataApiError.NetworkError e -> ApiError.connectionFailed(e.message());

            case FootballDataApiError.RateLimitExceeded e -> ApiError.rateLimited();

            case FootballDataApiError.NotFound e -> ApiError.of(e.message(), 404);

            case FootballDataApiError.Unauthorized e -> ApiError.of(e.message(), 401);

            case FootballDataApiError.ServerError e -> ApiError.of(e.message(), e.statusCode());

            case FootballDataApiError.UnknownError e -> ApiError.of(e.message(), 0);

            case FootballDataApiError.UnexpectedError e -> ApiError.of(e.message(), 500);
        };
    }
}
