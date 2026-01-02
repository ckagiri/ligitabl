package com.ligitabl.api.usecases.importer;

import com.ligitabl.api.importer.footballdata.FootballDataGateway;
import com.ligitabl.api.importer.model.Entities;
import com.ligitabl.api.importer.model.ImportError;
import com.ligitabl.api.importer.model.ValueObjects;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.importer.event.ImportEventPublisher;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Round;
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
    public Either<ImportError, Entities.ImportSummary> execute(ValueObjects.CompetitionCode competitionCode) {
        log.info("Starting match import for competition: {}", competitionCode);

        return fetchAndResolveSeason(competitionCode.getValue())
                .flatMap(context -> fetchMatches(context)
                        .flatMap(this::processMatches)
                        .map(summary -> {
                            eventPublisher.publishImportCompleted(summary);
                            return summary;
                        }));
    }

    /**
     * Fetch competition and resolve the season
     */
    private Either<ImportError, ImportContext> fetchAndResolveSeason(String code) {
        return footballDataGateway.fetchCompetition(code)
                .flatMap(competition -> {
                    log.debug("Fetched competition: {}", competition.getName());

                    return seasonRepo.findByClientId(competition.getCurrentSeason().getId())
                            .map(season -> {
                                log.debug("Resolved season: {} (clientId: {})",
                                        season.getName(),
                                        season.getClientId());
                                return new ImportContext(code, season, new ArrayList<>());
                            });
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
    private Either<ImportError, Entities.ImportSummary> processMatches(ImportContext context) {
        log.info("Processing {} matches...", context.matches.size());

        List<Entities.MatchImportResult> successes = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        // Process each match, collecting successes and failures
        for (Entities.ExternalMatch match : context.matches) {
            Either<ImportError, Entities.MatchImportResult> result = processMatch(match, context.season);

            if (result.isRight()) {
                successes.add(result.get());
            } else {
                errors.add(result.getLeft());
            }
        }

        int created = (int) successes.stream().filter(Entities.MatchImportResult::isCreated).count();
        int updated = (int) successes.stream().filter(Entities.MatchImportResult::isUpdated).count();

        Entities.ImportSummary summary = Entities.ImportSummary.builder()
                .competition(context.competitionCode)
                .seasonName(context.season.getName())
                .totalMatches(context.matches.size())
                .created(created)
                .updated(updated)
                .failed(errors.size())
                .errors(errors)
                .build();

        log.info("Import completed: {}", summary.getSummaryMessage());
        return right(summary);
    }

    /**
     * Process a single match
     */
    private Either<ImportError, Entities.MatchImportResult> processMatch(
            Entities.ExternalMatch externalMatch,
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
            Entities.ExternalMatch externalMatch,
            Season season) {

        return resolveRound(season.getId(), externalMatch.getMatchday().getValue())
                .flatMap(round -> resolveTeams(externalMatch)
                        .map(teams -> buildMatch(externalMatch, season, round, teams)));
    }

    /**
     * Resolve round for the match
     */
    private Either<ImportError, Round> resolveRound(UUID seasonId, int matchday) {
        return roundRepo.findBySeasonIdAndPosition(seasonId, matchday)
                .orElse(error -> {
                    log.error("Round not found for seasonId={}, matchday={}", seasonId, matchday);
                    return ImportError.DatabaseError.notFound("Round",
                            "seasonId=" + seasonId + ", matchday=" + matchday);
                });
    }

    /**
     * Resolve both teams
     */
    private Either<ImportError, TeamPair> resolveTeams(Entities.ExternalMatch match) {
        return teamRepo.findByClientId(match.getHomeTeam().getId().getValue())
                .flatMap(homeTeam -> teamRepo.findByClientId(match.getAwayTeam().getId().getValue())
                        .map(awayTeam -> new TeamPair(homeTeam, awayTeam)));
    }

    /**
     * Build domain match from resolved entities
     */
    private Match buildMatch(
            Entities.ExternalMatch externalMatch,
            Season season,
            Round round,
            TeamPair teams) {

        ValueObjects.MatchSlug slug = ValueObjects.MatchSlug.of(
                teams.home.getTla(),
                teams.away.getTla()
        );

        return Match.builder()
                .clientId(externalMatch.getId().getValue())
                .seasonId(season.getId())
                .roundId(round.getId())
                .homeTeamId(teams.home.id())
                .awayTeamId(teams.away.id())
                .slug(slug.getValue())
                .status(externalMatch.getStatus())
                .kickOff(externalMatch.getKickOff())
                .matchday(externalMatch.getMatchday())
                .score(externalMatch.getScore())
                .build();
    }

    /**
     * Persist match (create or update)
     */
    private Either<ImportError, Entities.MatchImportResult> persistMatch(Match match) {
        return matchRepo.findByClientId(match.getClientId())
                .map(
                        // Found - update existing
                        existing -> matchRepo.update(match.withDatabaseId(existing.getId()))
                                .map(updated -> Entities.MatchImportResult.updated(
                                        match.getClientId(),
                                        match.getSlug()
                                ))
                )
                .orElse(

                        // Not found - create new
                        error -> {
                            var created = matchRepo.create(match);
                            return Entities.MatchImportResult.created(
                                    match.getClientId(),
                                    match.getSlug())
                        }
                );
    }

    // Helper records
    private record ImportContext(
            ValueObjects.CompetitionCode competitionCode,
            Season season,
            List<Entities.ExternalMatch> matches
    ) {
        ImportContext withMatches(List<Entities.ExternalMatch> matches) {
            return new ImportContext(competitionCode, season, matches);
        }
    }

    private record TeamPair(Team home, Team away) {
    }
}
