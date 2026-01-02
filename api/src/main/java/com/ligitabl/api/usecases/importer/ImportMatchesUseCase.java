package com.ligitabl.api.usecases.importer;

import com.ligitabl.api.importer.footballdata.FootballDataGateway;
import com.ligitabl.api.importer.model.entities.ExternalMatch;
import com.ligitabl.api.importer.model.entities.ImportSummary;
import com.ligitabl.api.importer.model.entities.MatchImportResult;
import com.ligitabl.api.importer.model.errors.DatabaseError;
import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.errors.MappingError;
import com.ligitabl.api.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.importer.model.valueobjects.ExternalId;
import com.ligitabl.api.importer.model.valueobjects.MatchSlug;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.importer.event.ImportEventPublisher;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ligitabl.api.shared.Either.right;

@Slf4j
@RequiredArgsConstructor
public class ImportMatchesUseCase {

    private final FootballDataGateway footballDataGateway;
    private final SeasonRepo seasonRepo;
    private final TeamRepo teamRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final ImportEventPublisher eventPublisher;

    /**
     * Execute the import workflow for a competition.
     *
     * @param competitionCode The competition code (e.g., "PL", "SA")
     * @return Either an ImportError or ImportSummary
     */
    public Either<ImportError, ImportSummary> execute(CompetitionCode competitionCode) {
        log.info("Starting match import for competition: {}", competitionCode);

        return fetchAndResolveSeason(competitionCode)
            .flatMap(this::fetchMatches)
            .map(this::processMatches)
            .peek(eventPublisher::publishImportCompleted);
    }

    /**
     * Fetch competition and resolve the season
     */
    private Either<ImportError, ImportContext> fetchAndResolveSeason(CompetitionCode code) {
        return footballDataGateway.fetchCompetition(code)
                .flatMap(competition -> {
                    Integer seasonClientId = competition.getCurrentSeason().getId().getValue();
                    return Either.<ImportError, Season>ofOptional(
                                    seasonRepo.findByClientId(seasonClientId),
                                    () -> DatabaseError.notFound("Season", seasonClientId))
                            .map(season -> new ImportContext(code, season, List.of()));
                });
    }

    /**
     * Fetch external matches for the competition
     */
    private Either<ImportError, ImportContext> fetchMatches(ImportContext context) {
        return footballDataGateway.fetchMatches(context.competitionCode)
                .map(matches -> {
                    log.info("Fetched {} matches from external API", matches.size());
                    return context.withMatches(matches);
                });
    }

    /**
     * Process all matches and collect results
     */
    private ImportSummary processMatches(ImportContext context) {
        log.info("Processing {} matches...", context.matches.size());

        List<MatchImportResult> successes = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        // Process each match, collecting successes and failures
        for (ExternalMatch match : context.matches) {
            Either<ImportError, MatchImportResult> result = processMatch(match, context.season);

            if (result.isRight()) {
                successes.add(result.get());
            } else {
                errors.add(result.getLeft());
            }
        }

        int created = (int) successes.stream().filter(MatchImportResult::isCreated).count();
        int updated = (int) successes.stream().filter(MatchImportResult::isUpdated).count();

        ImportSummary summary = ImportSummary.builder()
                .competition(context.competitionCode)
                .seasonName(context.season.getName())
                .totalMatches(context.matches.size())
                .created(created)
                .updated(updated)
                .failed(errors.size())
                .errors(errors)
                .build();

        log.info("Import completed: {}", summary.getSummaryMessage());
        return summary;
    }

    /**
     * Process a single match
     */
        private Either<ImportError, MatchImportResult> processMatch(
            ExternalMatch externalMatch,
            Season season) {

        return mapToMatch(externalMatch, season)
                .flatMap(this::persistMatch)
                .peek(result -> {
                    if (result.isCreated()) {
                        eventPublisher.publishMatchCreated(result);
                    } else {
                        eventPublisher.publishMatchUpdated(result);
                    }
                })
                .peekLeft(error -> {
                    eventPublisher.publishMatchFailed(externalMatch.getId(), error);
                    log.warn("Failed to process match {}: {}",
                            externalMatch.getId().getValue(),
                            error.message());
                });
    }

    /**
     * Map external match to domain match
     */
        private Either<ImportError, Match> mapToMatch(
            ExternalMatch externalMatch,
            Season season) {

        return resolveRound(season.getId(), externalMatch.getMatchday().getValue())
            .flatMap(round -> resolveTeams(externalMatch)
                .map(teams -> buildMatch(externalMatch, season, round, teams)));
    }

    /**
     * Resolve round for the match
     */
    private Either<ImportError, Round> resolveRound(UUID seasonId, int matchday) {
        return Either.ofOptional(
            roundRepo.findBySeasonIdAndPosition(seasonId, matchday),
            () -> DatabaseError.notFound(
                "Round",
                "seasonId=" + seasonId + ", matchday=" + matchday));
    }

    /**
     * Resolve both teams
     */
    private Either<ImportError, TeamPair> resolveTeams(ExternalMatch match) {
        Integer homeClientId = match.getHomeTeam().getId().getValue();
        Integer awayClientId = match.getAwayTeam().getId().getValue();

        return Either.<ImportError, Team>ofOptional(
                teamRepo.findByClientId(homeClientId),
                () -> MappingError.missingReference("Team", homeClientId))
            .flatMap(homeTeam -> Either.<ImportError, Team>ofOptional(
                    teamRepo.findByClientId(awayClientId),
                    () -> MappingError.missingReference("Team", awayClientId))
                .map(awayTeam -> new TeamPair(homeTeam, awayTeam)));
    }

    /**
     * Build domain match from resolved entities
     */
    private Match buildMatch(
            ExternalMatch externalMatch,
            Season season,
            Round round,
            TeamPair teams) {

        MatchSlug slug = MatchSlug.of(
            teams.home.getTla(),
            teams.away.getTla()
        );

        return Match.builder()
            .clientId(externalMatch.getId().getValue())
                .seasonId(season.getId())
                .roundId(round.getId())
            .homeTeamId(teams.home.getId())
            .awayTeamId(teams.away.getId())
                .slug(slug.getValue())
                .status(toModelStatus(externalMatch.getStatus()))
            .kickOff(externalMatch.getKickOff().getValue())
            .matchday(externalMatch.getMatchday().getValue())
                .score(externalMatch.getScore()
                        .map(s -> Score.builder().homeGoals(s.homeGoals()).awayGoals(s.awayGoals()).build())
                        .orElse(null))
                .build();
    }

    private static MatchStatus toModelStatus(com.ligitabl.api.importer.model.valueobjects.MatchStatus status) {
        return switch (status.getStatus()) {
            case SCHEDULED, TIMED -> MatchStatus.SCHEDULED;
            case IN_PLAY, PAUSED -> MatchStatus.LIVE;
            case FINISHED, AWARDED -> MatchStatus.FINISHED;
            case POSTPONED -> MatchStatus.POSTPONED;
            case SUSPENDED -> MatchStatus.SUSPENDED;
            case CANCELLED -> MatchStatus.CANCELLED;
        };
    }

    /**
     * Persist match (create or update)
     */
    private Either<ImportError, MatchImportResult> persistMatch(Match match) {
        try {
            var existing = matchRepo.findByClientId(match.getClientId());
            MatchSlug slug = new MatchSlug(match.getSlug());

            if (existing.isPresent()) {
            match.withDatabaseId(existing.get().getId());
            matchRepo.update(match);
            return right(MatchImportResult.updated(
                new ExternalId(match.getClientId()),
                slug));
            }

            matchRepo.create(match);
            return right(MatchImportResult.created(
                new ExternalId(match.getClientId()),
                slug));
        } catch (Exception e) {
            return Either.left(DatabaseError.persistenceFailed("Match", e.getMessage()));
        }
    }

    // Helper records
    private record ImportContext(
            CompetitionCode competitionCode,
            Season season,
            List<ExternalMatch> matches
    ) {
        ImportContext withMatches(List<ExternalMatch> matches) {
            return new ImportContext(competitionCode, season, matches);
        }
    }

    private record TeamPair(Team home, Team away) {
    }
}
