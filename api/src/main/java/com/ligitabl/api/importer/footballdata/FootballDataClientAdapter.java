package com.ligitabl.api.importer.footballdata;

import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.client.footballdata.*;
import com.ligitabl.api.importer.model.Entities;
import com.ligitabl.api.importer.model.ImportError;
import com.ligitabl.api.importer.model.ValueObjects;
import com.ligitabl.api.shared.Either;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static com.ligitabl.api.shared.Either.left;
import static com.ligitabl.api.shared.Either.right;

@Slf4j
@Component
@RequiredArgsConstructor
public class FootballDataClientAdapter implements FootballDataGateway {

    private final FootballDataClient client;

    @Override
    public Either<ImportError, Entities.ExternalCompetition> fetchCompetition(ValueObjects.CompetitionCode code) {
        log.debug("Fetching competition via adapter: {}", code.getValue());

        // Call your existing client - it already returns Either!
        return client.getCompetition(code.getValue())
                // Map your ApiError to domain ImportError
                .mapLeft(this::mapApiError)
                // Map your DTO to domain entity
                .flatMap(this::mapCompetitionResponse);
    }

    @Override
    public Either<ImportError, List<Entities.ExternalMatch>> fetchMatches(ValueObjects.CompetitionCode code) {
        log.debug("Fetching matches via adapter: {}", code.getValue());

        // Use getUpcomingMatches - you can change this to use a different method
        return client.getUpcomingMatches(code.getValue())
                .mapLeft(this::mapApiError)
                .flatMap(this::mapMatchesResponse);
    }

    /**
     * Map your ApiError to domain ImportError
     */
    private ImportError mapApiError(FootballDataClient.ApiError error) {
        return switch (error) {
            case FootballDataClient.ApiError.NetworkError e ->
                    ImportError.ApiError.connectionFailed(e.message());

            case ApiError.RateLimitExceeded e ->
                    ImportError.ApiError.rateLimited();

            case ApiError.NotFound e ->
                    ImportError.ApiError.of(e.message(), 404);

            case FootballDataClient.ApiError.Unauthorized e ->
                    ImportError.ApiError.of(e.message(), 401);

            case ApiError.ServerError e ->
                    ImportError.ApiError.of(e.message(), e.statusCode());

            case ApiError.UnknownError e ->
                    ImportError.ApiError.of(e.message(), 0);

            case ApiError.UnexpectedError e ->
                    ImportError.ApiError.of(e.message(), 500);
        };
    }

    /**
     * Map your CompetitionResponse to domain ExternalCompetition
     */
    private Either<ImportError, Entities.ExternalCompetition> mapCompetitionResponse(
            CompetitionResponse response) {

        if (response.currentSeason() == null) {
            return left(ImportError.ApiError.of(
                    "Competition has no current season", 200));
        }

        CurrentSeason currentSeason = response.currentSeason();

        return Entities.ExternalSeason.create(
                        currentSeason.id().intValue(), // Convert Long to Integer
                        currentSeason.startDate().toString(),
                        currentSeason.endDate().toString()
                )
                .flatMap(season -> Entities.ExternalCompetition.create(
                        response.id().intValue(), // Convert Long to Integer
                        response.name(),
                        response.code(),
                        season
                ));
    }

    /**
     * Map your MatchesResponse to domain ExternalMatch list
     */
    private Either<ImportError, List<Entities.ExternalMatch>> mapMatchesResponse(
            MatchesResponse response) {

        if (response.matches() == null || response.matches().isEmpty()) {
            log.debug("No matches in response");
            return right(List.of());
        }

        // Map each match DTO to domain ExternalMatch
        var results = response.matches().stream()
                .map(this::mapMatchDto)
                .collect(Collectors.toList());

        // Check if any mapping failed
        var failures = results.stream()
                .filter(Either::isLeft)
                .map(Either::getLeft)
                .collect(Collectors.toList());

        if (!failures.isEmpty()) {
            log.warn("Failed to map {} matches", failures.size());
            // Return first error
            return left(failures.get(0));
        }

        // Extract successful mappings
        var matches = results.stream()
                .filter(Either::isRight)
                .map(Either::get)
                .collect(Collectors.toList());

        log.debug("Successfully mapped {} matches", matches.size());
        return right(matches);
    }

    /**
     * Map your MatchDto to domain ExternalMatch
     */
    private Either<ImportError, Entities.ExternalMatch> mapMatchDto(MatchDto dto) {
        // Map teams
        Either<ImportError, Entities.ExternalTeam> homeTeamResult = mapTeam(dto.homeTeam());
        Either<ImportError, Entities.ExternalTeam> awayTeamResult = mapTeam(dto.awayTeam());

        // Combine results using flatMap
        return homeTeamResult.flatMap(homeTeam ->
                awayTeamResult.flatMap(awayTeam ->
                        Entities.ExternalMatch.create(
                                dto.id().intValue(), // Convert Long to Integer
                                dto.utcDate(),
                                dto.status(),
                                dto.matchday(),
                                homeTeam,
                                awayTeam
                        )
                )
        );
    }

    /**
     * Map HomeTeam to domain ExternalTeam
     */
    private Either<ImportError, Entities.ExternalTeam> mapTeam(HomeTeam team) {
        if (team == null || team.id() == null) {
            return left(ImportError.ValidationError.of(
                    "Team data is missing", "team"));
        }

        return Entities.ExternalTeam.create(
                team.id().intValue(), // Convert Long to Integer
                team.name(),
                team.tla()
        );
    }

    /**
     * Map AwayTeam to domain ExternalTeam
     */
    private Either<ImportError, Entities.ExternalTeam> mapTeam(AwayTeam team) {
        if (team == null || team.id() == null) {
            return left(ImportError.ValidationError.of(
                    "Team data is missing", "team"));
        }

        return Entities.ExternalTeam.create(
                team.id().intValue(), // Convert Long to Integer
                team.name(),
                team.tla()
        );
    }
}
