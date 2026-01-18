package com.ligitabl.api.importer.footballdata;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.client.FootballDataClient.ApiError;
import com.ligitabl.api.client.footballdata.*;
import com.ligitabl.api.importer.model.entities.ExternalCompetition;
import com.ligitabl.api.importer.model.entities.ExternalMatch;
import com.ligitabl.api.importer.model.entities.ExternalSeason;
import com.ligitabl.api.importer.model.entities.ExternalTeam;
import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.errors.ValidationError;
import com.ligitabl.api.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.shared.Either;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FootballDataClientAdapter implements FootballDataGateway {

    private final FootballDataClient client;

    @Override
    public Either<ImportError, ExternalCompetition> fetchCompetition(CompetitionCode code) {
        log.debug("Fetching competition via adapter: {}", code.getValue());

        return client.getCompetition(code.getValue()).mapLeft(this::mapApiError).flatMap(this::mapCompetitionResponse);
    }

    @Override
    public Either<ImportError, List<ExternalMatch>> fetchMatchesForCompetition(CompetitionCode code) {
        log.debug("Fetching matches via adapter: {}", code.getValue());

        return client.getMatchesForCompetition(code.getValue())
                .mapLeft(this::mapApiError)
                .flatMap(this::mapMatchesResponse);
    }

    private ImportError mapApiError(ApiError error) {
        return switch (error) {
            case ApiError.NetworkError e -> com.ligitabl.api.importer.model.errors.ApiError.connectionFailed(
                    e.message());

            case ApiError.RateLimitExceeded e -> com.ligitabl.api.importer.model.errors.ApiError.rateLimited();

            case ApiError.NotFound e -> com.ligitabl.api.importer.model.errors.ApiError.of(e.message(), 404);

            case ApiError.Unauthorized e -> com.ligitabl.api.importer.model.errors.ApiError.of(e.message(), 401);

            case ApiError.ServerError e -> com.ligitabl.api.importer.model.errors.ApiError.of(
                    e.message(), e.statusCode());

            case ApiError.UnknownError e -> com.ligitabl.api.importer.model.errors.ApiError.of(e.message(), 0);

            case ApiError.UnexpectedError e -> com.ligitabl.api.importer.model.errors.ApiError.of(e.message(), 500);
        };
    }

    /**
     * Map CompetitionResponse to ExternalCompetition
     */
    private Either<ImportError, ExternalCompetition> mapCompetitionResponse(CompetitionResponse response) {

        if (response.currentSeason() == null) {
            return left(com.ligitabl.api.importer.model.errors.ApiError.of("Competition has no current season", 200));
        }

        CurrentSeason currentSeason = response.currentSeason();

        return ExternalSeason.create(
                        currentSeason.id().intValue(), // Convert Long to Integer
                        currentSeason.startDate().toString(),
                        currentSeason.endDate().toString(),
                        currentSeason.currentMatchday())
                .flatMap(season -> ExternalCompetition.create(
                        response.id().intValue(), // Convert Long to Integer
                        response.name(),
                        response.code(),
                        season));
    }

    /**
     * Map MatchesResponse to ExternalMatch list
     */
    private Either<ImportError, List<ExternalMatch>> mapMatchesResponse(MatchesResponse response) {

        if (response.matches() == null || response.matches().isEmpty()) {
            log.debug("No matches in response");
            return right(List.of());
        }

        // Map each match DTO to ExternalMatch
        var results = response.matches().stream().map(this::mapMatchDto).collect(Collectors.toList());

        // Check if any mapping failed
        var failures =
                results.stream().filter(Either::isLeft).map(Either::getLeft).collect(Collectors.toList());

        if (!failures.isEmpty()) {
            log.warn("Failed to map {} matches", failures.size());
            // Return first error
            return left(failures.get(0));
        }

        // Extract successful mappings
        var matches = results.stream().filter(Either::isRight).map(Either::get).collect(Collectors.toList());

        log.debug("Successfully mapped {} matches", matches.size());
        return right(matches);
    }

    private Either<ImportError, ExternalMatch> mapMatchDto(MatchDto dto) {
        // Map teams
        Either<ImportError, ExternalTeam> homeTeamResult = mapTeam(dto.homeTeam());
        Either<ImportError, ExternalTeam> awayTeamResult = mapTeam(dto.awayTeam());

        final Integer homeGoals = (dto.score() != null && dto.score().fullTime() != null)
                ? dto.score().fullTime().home()
                : null;
        final Integer awayGoals = (dto.score() != null && dto.score().fullTime() != null)
                ? dto.score().fullTime().away()
                : null;

        // Combine results using flatMap
        return homeTeamResult.flatMap(homeTeam -> awayTeamResult.flatMap(awayTeam -> ExternalMatch.create(
                dto.id().intValue(), // Convert Long to Integer
                dto.utcDate(),
                dto.status(),
                dto.matchday(),
                homeTeam,
                awayTeam,
                homeGoals,
                awayGoals)));
    }

    /**
     * Map HomeTeam to domain ExternalTeam
     */
    private Either<ImportError, ExternalTeam> mapTeam(HomeTeam team) {
        if (team == null || team.id() == null) {
            return left(ValidationError.of("Team data is missing", "team"));
        }

        return ExternalTeam.create(
                team.id().intValue(), // Convert Long to Integer
                team.name(),
                team.tla());
    }

    /**
     * Map AwayTeam to domain ExternalTeam
     */
    private Either<ImportError, ExternalTeam> mapTeam(AwayTeam team) {
        if (team == null || team.id() == null) {
            return left(ValidationError.of("Team data is missing", "team"));
        }

        return ExternalTeam.create(
                team.id().intValue(), // Convert Long to Integer
                team.name(),
                team.tla());
    }
}
