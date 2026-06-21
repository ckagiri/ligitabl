package com.ligitabl.api.scheduling.syncmatches;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.client.FootballDataApiError;
import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.client.footballdata.MatchDto;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.match.MatchUpdateHelper;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncMatchesUseCase {

    private final FootballDataClient footballDataClient; // Port interface (not implementation!)
    private final HierarchyValidator hierarchyValidator;
    private final MatchRepo matchRepo;
    private final AsyncStandingsService standingsService;
    private final CompetitionDefaults competitionDefaults;
    private final LiveMatchTracker liveMatchTracker;
    private final MatchUpdateHelper matchUpdateHelper;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    public record SyncMatchesCommand() {}

    public sealed interface SyncMatchesError {
        record HierarchyError(UseCaseError error) implements SyncMatchesError {}

        record DataAccessError(FootballDataApiError error) implements SyncMatchesError {}
    }

    @Transactional
    public Either<SyncMatchesError, MatchSyncResult> execute(SyncMatchesCommand command) {
        log.info("Starting match synchronization for competition: {}", competitionCode);

        return resolveHierarchy()
                .map(this::updateTracking) // Track live match transitions
                .flatMap(this::determineAndFetchMatches) // Smart endpoint selection
                .flatMap(this::updateMatches)
                .flatMap(this::recalculateStandings)
                .map(this::calculateNextSync);
    }

    private Either<SyncMatchesError, RoundContext> resolveHierarchy() {
        var competitionSlug = competitionDefaults.defaultCompetitionSlug();

        return hierarchyValidator
                .resolveHierarchy(competitionSlug)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.HierarchyError(error))
                .map(ctx -> {
                    var existingMatches = matchRepo.findByRoundId(ctx.round().getId());
                    return new RoundContext(ctx.season(), ctx.round(), existingMatches, null);
                });
    }

    /**
     * Update live match tracking before fetching new data.
     */
    private RoundContext updateTracking(RoundContext context) {
        var trackingResult = liveMatchTracker.updateTracking(context.existingMatches());

        log.debug(
                "Tracking state: hasLive={}, finished={}, started={}",
                trackingResult.hasLive(),
                trackingResult.finishedMatches().size(),
                trackingResult.startedMatches().size());

        return new RoundContext(context.season(), context.round(), context.existingMatches(), trackingResult);
    }

    /**
     * Determines which API endpoint to use based on tracking state and match timing.
     */
    private Either<SyncMatchesError, FetchedMatchData> determineAndFetchMatches(RoundContext context) {
        var existingMatches = context.existingMatches();
        var trackingResult = context.trackingResult();

        // Strategy 1: Live match just finished - MUST fetch TODAY to get final state
        if (trackingResult != null && trackingResult.needsTodayFetch()) {
            log.info("Live match finished - fetching TODAY to capture final state");
            return fetchTodayMatches(context);
        }

        // Strategy 2: Matches currently live - fetch LIVE endpoint with validation.
        // Falls back to TODAY if DB-tracked live matches are missing from the API response,
        // which catches stale LIVE status after a restart and race conditions where a match
        // finishes between the DB read and the API call.
        if (trackingResult != null && trackingResult.hasLive()) {
            log.debug("Live matches in progress - using LIVE endpoint with validation");

            var dbLiveMatchIds = existingMatches.stream()
                    .filter(m -> m.getStatus() == MatchStatus.LIVE)
                    .map(Match::getClientId)
                    .filter(id -> id != null)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());

            return fetchLiveMatches(context).flatMap(fetchedData -> {
                var apiLiveMatchIds = fetchedData.matches().stream()
                        .map(m -> m.id().toString())
                        .collect(Collectors.toSet());

                var missingMatches = dbLiveMatchIds.stream()
                        .filter(id -> !apiLiveMatchIds.contains(id))
                        .collect(Collectors.toSet());

                if (!missingMatches.isEmpty()) {
                    log.warn(
                            "DB has {} LIVE matches not in API response - fetching YESTERDAY+TODAY: {}",
                            missingMatches.size(),
                            missingMatches);
                    return fetchYesterdayAndTodayMatches(context);
                }

                return Either.right(fetchedData);
            });
        }

        // Strategy 3: Check for imminent/soon kickoffs
        var nextKickoff = existingMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                .map(Match::getKickOff)
                .filter(k -> k != null)
                .min(OffsetDateTime::compareTo);

        if (nextKickoff.isPresent()) {
            var minutesUntilKickoff =
                    Duration.between(OffsetDateTime.now(), nextKickoff.get()).toMinutes();

            if (minutesUntilKickoff <= 60) {
                log.debug("Kickoff in {} minutes - using TODAY endpoint", minutesUntilKickoff);
                return fetchTodayMatches(context);
            }

            if (minutesUntilKickoff < 360) {
                log.debug("Kickoff in {} hours - using TODAY endpoint", minutesUntilKickoff / 60);
                return fetchTodayMatches(context);
            }
        }

        // Strategy 4: Default - check today + tomorrow
        log.debug("No imminent matches - using DATE RANGE endpoint (today + tomorrow)");
        return fetchUpcomingMatches(context);
    }

    /**
     * Fetch via GET /matches?status=LIVE
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchLiveMatches(RoundContext context) {
        return footballDataClient
                .getLiveMatches(competitionCode)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    /**
     * Fetch via GET /matches?date=today
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchTodayMatches(RoundContext context) {
        var today = LocalDate.now();

        return footballDataClient
                .getMatchesForDate(competitionCode, today)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    /**
     * Fetch via GET /matches?dateFrom=2daysAgo&dateTo=tomorrow
     * dateTo is exclusive, so tomorrow is needed to include today's matches.
     * Used when DB-tracked live matches are missing from the LIVE endpoint response,
     * covering matches that finished past midnight or were processed a day late.
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchYesterdayAndTodayMatches(RoundContext context) {
        var yesterday = LocalDate.now().minusDays(2);
        var today = LocalDate.now().plusDays(1);

        return footballDataClient
                .getMatchesInDateRange(competitionCode, yesterday, today)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    /**
     * Fetch via GET /matches?dateFrom=today&dateTo=tomorrow
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchUpcomingMatches(RoundContext context) {
        var today = LocalDate.now();
        var dayAfterTomorrow = today.plusDays(2);

        return footballDataClient
                .getMatchesInDateRange(competitionCode, today, dayAfterTomorrow)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    private Either<SyncMatchesError, SyncContext> updateMatches(FetchedMatchData fetchedData) {
        var context = fetchedData.roundContext();
        var apiMatches = fetchedData.matches();
        var existingMatches = context.existingMatches();

        log.debug(
                "Updating matches: apiMatches={}, existingMatches={}, roundId={}",
                apiMatches.size(),
                existingMatches.size(),
                context.round().getId());

        var finishedMatchIds = new ArrayList<UUID>();
        int matchesUpdated = 0;

        for (var apiMatch : apiMatches) {
            var existing = findMatchByExternalId(existingMatches, apiMatch.id().toString());

            if (existing != null) {
                var updated = updateMatch(existing, apiMatch);

                if (updated.hasChanged()) {
                    matchRepo.save(updated.match());
                    matchesUpdated++;

                    if (updated.becameFinished()) {
                        finishedMatchIds.add(updated.match().getId());
                    }
                } else {
                    log.debug(
                            "No changes for match clientId={}, status={}, kickOff={}",
                            existing.getClientId(),
                            existing.getStatus(),
                            existing.getKickOff());
                }
            } else {
                log.debug("No existing match found for apiMatchId={}", apiMatch.id());
            }
        }

        log.info(
                "Matches processed: {}, updated: {}, newly finished: {}",
                apiMatches.size(),
                matchesUpdated,
                finishedMatchIds.size());

        // Reload to get updated status
        var updatedMatches = matchRepo.findByRoundId(context.round().getId());

        return Either.right(new SyncContext(
                context.season(),
                context.round(),
                apiMatches.size(),
                matchesUpdated,
                finishedMatchIds,
                updatedMatches));
    }

    private Either<SyncMatchesError, SyncContext> recalculateStandings(SyncContext context) {
        if (context.finishedMatchIds().isEmpty()) {
            return Either.right(context);
        }

        // Async - don't wait
        standingsService.recalculateAsync(
                context.season().getId(), context.round().getPosition());

        return Either.right(context);
    }

    private MatchSyncResult calculateNextSync(SyncContext context) {
        var matches = context.updatedMatches();

        boolean allComplete = matches.stream().allMatch(this::isComplete);

        boolean hasBlocking = matches.stream().anyMatch(this::isBlocking);

        boolean allTerminalOrBlocking = matches.stream().allMatch(m -> isComplete(m) || isBlocking(m));
        boolean roundObstructed = allTerminalOrBlocking && hasBlocking;

        var obstructedMatchIds = roundObstructed
                ? matches.stream().filter(this::isBlocking).map(Match::getId).toList()
                : List.<UUID>of();

        // Check if no upcoming matches (empty result)
        if (context.matchesProcessed() == 0) {
            return new MatchSyncResult(
                    context.season().getId(),
                    context.round().getId(),
                    context.round().getPosition(),
                    0,
                    0,
                    0,
                    List.of(),
                    false,
                    false,
                    false,
                    List.of(),
                    NextSyncSchedule.hours(12, "No upcoming matches - checking twice daily"));
        }

        boolean hasLive = matches.stream().anyMatch(m -> m.getStatus() == MatchStatus.LIVE);

        boolean hasScheduled = matches.stream().anyMatch(m -> m.getStatus() == MatchStatus.SCHEDULED);

        var nextKickoff = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                .map(Match::getKickOff)
                .filter(k -> k != null)
                .min(OffsetDateTime::compareTo)
                .orElse(null);

        var nextSchedule = SyncFrequencyCalculator.calculateNextSync(
                hasLive, hasScheduled, nextKickoff, allComplete, roundObstructed);

        return new MatchSyncResult(
                context.season().getId(),
                context.round().getId(),
                context.round().getPosition(),
                context.matchesProcessed(),
                context.matchesUpdated(),
                context.finishedMatchIds().size(),
                context.finishedMatchIds(),
                allComplete,
                hasBlocking,
                roundObstructed,
                obstructedMatchIds,
                nextSchedule);
    }

    private boolean isComplete(Match match) {
        if (match == null || match.getStatus() == null) {
            return false;
        }
        return match.getStatus() == MatchStatus.FINISHED || match.getStatus() == MatchStatus.POSTPONED;
    }

    private boolean isBlocking(Match match) {
        if (match == null || match.getStatus() == null) {
            return false;
        }
        return match.getStatus() == MatchStatus.CANCELLED || match.getStatus() == MatchStatus.SUSPENDED;
    }

    private Match findMatchByExternalId(List<Match> matches, String externalId) {
        return matches.stream()
                .filter(m -> m.getClientId() != null && externalId.equals(String.valueOf(m.getClientId())))
                .findFirst()
                .orElse(null);
    }

    private UpdateResult updateMatch(Match existing, MatchDto apiMatch) {
        var previousStatus = existing.getStatus();
        boolean changed = matchUpdateHelper.applyUpdate(existing, apiMatch);
        if (!changed) {
            return new UpdateResult(existing, false, false);
        }
        boolean becameFinished = previousStatus != MatchStatus.FINISHED && existing.getStatus() == MatchStatus.FINISHED;
        return new UpdateResult(existing, true, becameFinished);
    }

    private record RoundContext(
            Season season, Round round, List<Match> existingMatches, LiveMatchTracker.TrackingResult trackingResult) {}

    private record FetchedMatchData(RoundContext roundContext, List<MatchDto> matches) {}

    private record SyncContext(
            Season season,
            Round round,
            int matchesProcessed,
            int matchesUpdated,
            List<UUID> finishedMatchIds,
            List<Match> updatedMatches) {}

    private record UpdateResult(Match match, boolean hasChanged, boolean becameFinished) {}
}
