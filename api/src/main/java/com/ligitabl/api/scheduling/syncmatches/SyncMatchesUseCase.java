package com.ligitabl.api.scheduling.syncmatches;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    /**
     * The sync window is a calendar date, so it needs a zone: this resolves it in UTC, matching the
     * application's {@code Clock.systemUTC()} bean and the timestamps the upstream API returns.
     * Previously a bare {@code LocalDate.now()} in the JVM default zone — identical on a UTC
     * deployment, but unfreezable in a test and a day out on a developer machine east of UTC.
     */
    private final Clock clock;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    private static final int MAX_OVERDUE_LOOKBACK_DAYS = 8;

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
            var minutesUntilKickoff = Duration.between(OffsetDateTime.now(clock), nextKickoff.get())
                    .toMinutes();

            // A TODAY fetch would never see an overdue match from an earlier day, leaving it SCHEDULED
            // (and the round unfinishable) for good.
            var kickoffDate = nextKickoff.get().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            if (kickoffDate.isBefore(LocalDate.now(clock))) {
                log.info("Overdue match from {} - fetching from that date to today", kickoffDate);
                return fetchOverdueMatches(context, kickoffDate);
            }

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
        var today = LocalDate.now(clock);

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
        var yesterday = LocalDate.now(clock).minusDays(2);
        var today = LocalDate.now(clock).plusDays(1);

        return footballDataClient
                .getMatchesInDateRange(competitionCode, yesterday, today)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    /**
     * Fetch via GET /matches?dateFrom=overdueDate&dateTo=tomorrow, the start clamped because the API
     * caps a date range at 10 days.
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchOverdueMatches(RoundContext context, LocalDate from) {
        var today = LocalDate.now(clock);
        var earliest = today.minusDays(MAX_OVERDUE_LOOKBACK_DAYS);
        var dateFrom = from.isBefore(earliest) ? earliest : from;

        return footballDataClient
                .getMatchesInDateRange(competitionCode, dateFrom, today.plusDays(1))
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    /**
     * Fetch via GET /matches?dateFrom=today&dateTo=tomorrow
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchUpcomingMatches(RoundContext context) {
        var today = LocalDate.now(clock);
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
        int matchesOutsideRound = 0;
        boolean standingsAffected = false;

        for (var apiMatch : apiMatches) {
            var existing = findMatchByExternalId(existingMatches, apiMatch.id().toString());

            if (existing != null) {
                var updated = updateMatch(existing, apiMatch);

                if (updated.hasChanged()) {
                    matchRepo.save(updated.match());
                    matchesUpdated++;
                    standingsAffected |= updated.affectsStandings();

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
                matchesOutsideRound++;
                log.debug("No existing match found for apiMatchId={}", apiMatch.id());
            }
        }

        // outside current round > 0 with updated 0, round after round, means the round isn't advancing.
        log.info(
                "Matches processed: {}, updated: {}, newly finished: {}, outside current round: {}",
                apiMatches.size(),
                matchesUpdated,
                finishedMatchIds.size(),
                matchesOutsideRound);

        // Reload to get updated status
        var updatedMatches = matchRepo.findByRoundId(context.round().getId());

        return Either.right(new SyncContext(
                context.season(),
                context.round(),
                apiMatches.size(),
                matchesUpdated,
                finishedMatchIds,
                standingsAffected,
                updatedMatches));
    }

    private Either<SyncMatchesError, SyncContext> recalculateStandings(SyncContext context) {
        if (!context.standingsAffected()) {
            return Either.right(context);
        }

        var seasonId = context.season().getId();
        var roundPosition = context.round().getPosition();

        // The async worker reads matches in its own transaction, so started here it could run before
        // this one commits and recompute standings without the result that triggered it.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    standingsService.recalculateAsync(seasonId, roundPosition);
                }
            });
        } else {
            standingsService.recalculateAsync(seasonId, roundPosition);
        }

        return Either.right(context);
    }

    private MatchSyncResult calculateNextSync(SyncContext context) {
        var matches = context.updatedMatches();
        var roundStatus = context.round().computeStatus(matches);

        boolean allComplete = matches.stream().allMatch(m -> m.isComplete());

        boolean hasBlocking = matches.stream().anyMatch(m -> m.isBlocking());

        boolean allTerminalOrBlocking = matches.stream().allMatch(m -> m.isComplete() || m.isBlocking());
        boolean roundObstructed = allTerminalOrBlocking && hasBlocking;

        var obstructedMatchIds = roundObstructed
                ? matches.stream()
                        .filter(m -> m.isBlocking())
                        .map(m -> m.getId())
                        .toList()
                : List.<UUID>of();

        // Check if no upcoming matches (empty result)
        if (context.matchesProcessed() == 0) {
            return new MatchSyncResult(
                    context.season().getId(),
                    context.round().getId(),
                    context.round().getPosition(),
                    roundStatus,
                    0,
                    0,
                    0,
                    List.of(),
                    false,
                    false,
                    false,
                    List.of(),
                    NextSyncSchedule.hours(12, "No upcoming matches - checking twice daily", true));
        }

        boolean hasLive = matches.stream().anyMatch(m -> m.getStatus() == MatchStatus.LIVE);

        boolean hasScheduled = matches.stream().anyMatch(m -> m.getStatus() == MatchStatus.SCHEDULED);

        boolean hasSuspended = matches.stream().anyMatch(m -> m.getStatus() == MatchStatus.SUSPENDED);

        boolean hasCancelled = matches.stream().anyMatch(m -> m.getStatus() == MatchStatus.CANCELLED);

        var nextKickoff = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                .map(Match::getKickOff)
                .filter(k -> k != null)
                .min(OffsetDateTime::compareTo)
                .orElse(null);

        var nextSchedule = SyncFrequencyCalculator.calculateNextSync(
                hasLive,
                hasScheduled,
                hasSuspended,
                hasCancelled,
                nextKickoff,
                allComplete,
                roundObstructed,
                OffsetDateTime.now(clock));

        return new MatchSyncResult(
                context.season().getId(),
                context.round().getId(),
                context.round().getPosition(),
                roundStatus,
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
            return new UpdateResult(existing, false, false, false);
        }
        boolean nowFinished = existing.getStatus() == MatchStatus.FINISHED;
        boolean wasFinished = previousStatus == MatchStatus.FINISHED;
        // Standings count only FINISHED matches, so a score correction on one matters as much as a new result.
        return new UpdateResult(existing, true, !wasFinished && nowFinished, wasFinished || nowFinished);
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
            boolean standingsAffected,
            List<Match> updatedMatches) {}

    private record UpdateResult(Match match, boolean hasChanged, boolean becameFinished, boolean affectsStandings) {}
}
