package com.ligitabl.api.runners.importer;

import static com.ligitabl.api.shared.Either.right;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.api.runners.importer.event.ImportEventPublisher;
import com.ligitabl.api.runners.importer.footballdata.FootballDataGateway;
import com.ligitabl.api.runners.importer.model.entities.ExternalMatch;
import com.ligitabl.api.runners.importer.model.entities.ImportSummary;
import com.ligitabl.api.runners.importer.model.entities.MatchImportResult;
import com.ligitabl.api.runners.importer.model.errors.DatabaseError;
import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.errors.MappingError;
import com.ligitabl.api.runners.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.runners.importer.model.valueobjects.ExternalId;
import com.ligitabl.api.runners.importer.model.valueobjects.MatchSlug;
import com.ligitabl.api.shared.Either;
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
        return footballDataGateway.fetchCompetition(code).flatMap(competition -> {
            Integer seasonClientId = competition.getCurrentSeason().getId().getValue();
            Integer currentMatchday = competition.getCurrentSeason().getCurrentMatchday();
            return Either.<ImportError, Season>ofOptional(
                            seasonRepo.findByClientId(seasonClientId),
                            () -> DatabaseError.notFound("Season", seasonClientId))
                    .map(season -> new ImportContext(code, season, currentMatchday, List.of()));
        });
    }

    /**
     * Fetch external matches for the competition
     */
    private Either<ImportError, ImportContext> fetchMatches(ImportContext context) {
        return footballDataGateway
                .fetchMatchesForCompetition(context.competitionCode)
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
            Either<ImportError, MatchImportResult> result = processMatch(match, context);

            if (result.isRight()) {
                successes.add(result.get());
            } else {
                errors.add(result.getLeft());
            }
        }

        int created =
                (int) successes.stream().filter(MatchImportResult::isCreated).count();
        int updated =
                (int) successes.stream().filter(MatchImportResult::isUpdated).count();
        int unchanged =
                (int) successes.stream().filter(MatchImportResult::isUnchanged).count();

        RoundUpdateResult roundUpdate = updateCurrentRoundIfNeeded(context.season, context.currentMatchday);

        ImportSummary summary = ImportSummary.builder()
                .competition(context.competitionCode)
                .seasonName(context.season.getName())
                .totalMatches(context.matches.size())
                .created(created)
                .updated(updated)
                .unchanged(unchanged)
                .failed(errors.size())
                .currentRoundPosition(roundUpdate.currentRoundPosition())
                .currentRoundUpdated(roundUpdate.updated())
                .errors(errors)
                .build();

        log.info("Import completed: {}", summary.getSummaryMessage());
        return summary;
    }

    private RoundUpdateResult updateCurrentRoundIfNeeded(Season season, Integer currentMatchday) {
        if (currentMatchday == null || currentMatchday < 1) {
            return new RoundUpdateResult(false, resolveCurrentRoundPosition(season));
        }

        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            return new RoundUpdateResult(false, null);
        }

        var currentRound = roundRepo.findById(currentRoundId).orElse(null);
        if (currentRound == null) {
            return new RoundUpdateResult(false, null);
        }

        int currentPosition = currentRound.getPosition();
        if (currentMatchday <= currentPosition) {
            return new RoundUpdateResult(false, currentPosition);
        }

        if (currentMatchday != currentPosition + 1) {
            return new RoundUpdateResult(false, currentPosition);
        }

        var nextRoundOpt = roundRepo.findBySeasonIdAndPosition(season.getId(), currentMatchday);
        if (nextRoundOpt.isEmpty()) {
            return new RoundUpdateResult(false, currentPosition);
        }

        var nextRound = nextRoundOpt.get();
        season.setCurrentRoundId(nextRound.getId());
        season.setCurrentMatchDay(currentMatchday);
        seasonRepo.save(season);

        log.info(
                "Updated season current round from position {} to {} (roundId={})",
                currentPosition,
                currentMatchday,
                nextRound.getId());

        return new RoundUpdateResult(true, currentMatchday);
    }

    private Integer resolveCurrentRoundPosition(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            return null;
        }

        return roundRepo.findById(currentRoundId).map(Round::getPosition).orElse(null);
    }

    /**
     * Process a single match
     */
    private Either<ImportError, MatchImportResult> processMatch(ExternalMatch externalMatch, ImportContext context) {

        return mapToMatch(externalMatch, context)
                .flatMap(match -> persistMatch(match, context))
                .peek(result -> {
                    if (result.isCreated()) {
                        eventPublisher.publishMatchCreated(result);
                    } else if (result.isUpdated()) {
                        eventPublisher.publishMatchUpdated(result);
                    }
                })
                .peekLeft(error -> {
                    eventPublisher.publishMatchFailed(externalMatch.getId(), error);
                    log.warn(
                            "Failed to process match {}: {}",
                            externalMatch.getId().getValue(),
                            error.message());
                });
    }

    /**
     * Map external match to domain match
     */
    private Either<ImportError, Match> mapToMatch(ExternalMatch externalMatch, ImportContext context) {
        Season season = context.season;
        return resolveRound(context, season.getId(), externalMatch.getMatchday().getValue())
                .flatMap(round ->
                        resolveTeams(externalMatch).map(teams -> buildMatch(externalMatch, season, round, teams)));
    }

    /**
     * Resolve round for the match
     */
    private Either<ImportError, Round> resolveRound(ImportContext context, UUID seasonId, int matchday) {
        String cacheKey = seasonId + ":" + matchday;
        Optional<Round> cached = context.roundCacheBySeasonAndPosition.computeIfAbsent(
                cacheKey, k -> roundRepo.findBySeasonIdAndPosition(seasonId, matchday));
        return Either.ofOptional(
                cached, () -> DatabaseError.notFound("Round", "seasonId=" + seasonId + ", matchday=" + matchday));
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
    private Match buildMatch(ExternalMatch externalMatch, Season season, Round round, TeamPair teams) {

        MatchSlug slug = MatchSlug.of(teams.home.getTla(), teams.away.getTla());

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
                .score(externalMatch
                        .getScore()
                        .map(s -> Score.builder()
                                .homeGoals(s.homeGoals())
                                .awayGoals(s.awayGoals())
                                .build())
                        .orElse(null))
                .build();
    }

    private static MatchStatus toModelStatus(com.ligitabl.api.runners.importer.model.valueobjects.MatchStatus status) {
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
    private Either<ImportError, MatchImportResult> persistMatch(Match match, ImportContext context) {
        try {
            var existing = matchRepo.findByClientId(match.getClientId());
            MatchSlug slug = new MatchSlug(match.getSlug());
            ExternalId externalId = new ExternalId(match.getClientId());

            if (existing.isPresent()) {
                Match db = existing.get();
                if (isAdminRescheduled(db, context)) {
                    log.debug(
                            "Skipping admin-rescheduled match {} (matchday={}, roundId={})",
                            slug,
                            db.getMatchday(),
                            db.getRoundId());
                    return right(MatchImportResult.unchanged(externalId, slug));
                }
                if (!hasChanged(db, match)) {
                    return right(MatchImportResult.unchanged(externalId, slug));
                }
                match.withDatabaseId(db.getId());
                matchRepo.update(match);
                return right(MatchImportResult.updated(externalId, slug));
            }

            matchRepo.create(match);
            return right(MatchImportResult.created(externalId, slug));
        } catch (Exception e) {
            return Either.left(DatabaseError.persistenceFailed("Match", e.getMessage()));
        }
    }

    private boolean isAdminRescheduled(Match db, ImportContext context) {
        Optional<Round> round = context.roundCacheById.computeIfAbsent(db.getRoundId(), roundRepo::findById);
        return round.map(r -> r.getPosition() != db.getMatchday()).orElse(false);
    }

    private boolean hasChanged(Match db, Match incoming) {
        if (db.getStatus() != incoming.getStatus()) return true;
        if (!kickOffEquals(db.getKickOff(), incoming.getKickOff())) return true;
        if (!Objects.equals(db.getRoundId(), incoming.getRoundId())) return true;
        if (!Objects.equals(db.getScore(), incoming.getScore())) return true;
        return false;
    }

    private boolean kickOffEquals(java.time.OffsetDateTime a, java.time.OffsetDateTime b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.isEqual(b);
    }

    // Helper records
    private static final class ImportContext {
        final CompetitionCode competitionCode;
        final Season season;
        final Integer currentMatchday;
        final List<ExternalMatch> matches;
        final Map<UUID, Optional<Round>> roundCacheById = new HashMap<>();
        final Map<String, Optional<Round>> roundCacheBySeasonAndPosition = new HashMap<>();

        ImportContext(
                CompetitionCode competitionCode, Season season, Integer currentMatchday, List<ExternalMatch> matches) {
            this.competitionCode = competitionCode;
            this.season = season;
            this.currentMatchday = currentMatchday;
            this.matches = matches;
        }

        ImportContext withMatches(List<ExternalMatch> matches) {
            return new ImportContext(competitionCode, season, currentMatchday, matches);
        }
    }

    private record TeamPair(Team home, Team away) {}

    private record RoundUpdateResult(boolean updated, Integer currentRoundPosition) {}
}
