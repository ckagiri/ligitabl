package com.ligitabl.api.usecase.match.syncmatches;

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

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.match.shared.FeedMatch;
import com.ligitabl.api.usecase.match.shared.MatchFeed;
import com.ligitabl.api.usecase.match.shared.MatchUpdateHelper;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.model.domain.league.Match;
import com.ligitabl.model.domain.league.MatchStatus;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncMatchesUseCase {

    private final MatchFeed matchFeed;
    private final HierarchyValidator hierarchyValidator;
    private final MatchRepo matchRepo;
    private final AsyncStandingsService standingsService;
    private final CompetitionDefaults competitionDefaults;
    private final LiveMatchTracker liveMatchTracker;
    private final MatchUpdateHelper matchUpdateHelper;

    /**
     * The sync window is a calendar date, so it needs a zone: UTC, matching the {@code Clock} bean
     * and the upstream API's timestamps.
     */
    private final Clock clock;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    private static final int MAX_OVERDUE_LOOKBACK_DAYS = 8;

    @Transactional
    public Either<SyncMatchesError, MatchSyncResult> execute(SyncMatchesCommand command) {
        log.info("Starting match synchronization for competition: {}", competitionCode);

        return resolveHierarchy()
                .map(this::updateTracking) // Track live match transitions
                .flatMap(this::determineAndFetchMatches)
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

        // Matches live: fetch the LIVE endpoint, falling back to TODAY if DB-tracked live matches
        // are missing from it (stale LIVE status after a restart, or a match finishing between
        // the DB read and the API call).
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
                        .map(m -> String.valueOf(m.id()))
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
            var kickoffDate =
                    nextKickoff.get().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
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

    /** GET /matches?status=LIVE */
    private Either<SyncMatchesError, FetchedMatchData> fetchLiveMatches(RoundContext context) {
        return matchFeed
                .liveMatches(competitionCode)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(matches -> new FetchedMatchData(context, matches));
    }

    /** GET /matches?date=today */
    private Either<SyncMatchesError, FetchedMatchData> fetchTodayMatches(RoundContext context) {
        var today = LocalDate.now(clock);

        return matchFeed
                .matchesOn(competitionCode, today)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(matches -> new FetchedMatchData(context, matches));
    }

    /**
     * GET /matches?dateFrom=2daysAgo&dateTo=tomorrow; dateTo is exclusive. For DB-tracked live
     * matches missing from the LIVE response: finished past midnight, or processed a day late.
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchYesterdayAndTodayMatches(RoundContext context) {
        var yesterday = LocalDate.now(clock).minusDays(2);
        var today = LocalDate.now(clock).plusDays(1);

        return matchFeed
                .matchesBetween(competitionCode, yesterday, today)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(matches -> new FetchedMatchData(context, matches));
    }

    /**
     * Fetch via GET /matches?dateFrom=overdueDate&dateTo=tomorrow, the start clamped because the API
     * caps a date range at 10 days.
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchOverdueMatches(RoundContext context, LocalDate from) {
        var today = LocalDate.now(clock);
        var earliest = today.minusDays(MAX_OVERDUE_LOOKBACK_DAYS);
        var dateFrom = from.isBefore(earliest) ? earliest : from;

        return matchFeed
                .matchesBetween(competitionCode, dateFrom, today.plusDays(1))
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(matches -> new FetchedMatchData(context, matches));
    }

    /**
     * Fetch via GET /matches?dateFrom=today&dateTo=tomorrow
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchUpcomingMatches(RoundContext context) {
        var today = LocalDate.now(clock);
        var dayAfterTomorrow = today.plusDays(2);

        return matchFeed
                .matchesBetween(competitionCode, today, dayAfterTomorrow)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(matches -> new FetchedMatchData(context, matches));
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
            var existing = findMatchByExternalId(existingMatches, String.valueOf(apiMatch.id()));

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
            return MatchSyncResult.builder()
                    .seasonId(context.season().getId())
                    .roundId(context.round().getId())
                    .roundPosition(context.round().getPosition())
                    .roundStatus(roundStatus)
                    .matchesProcessed(0)
                    .matchesUpdated(0)
                    .newlyFinishedMatches(0)
                    .finishedMatchIds(List.of())
                    .allMatchesComplete(false)
                    .hasBlockingMatches(false)
                    .roundObstructed(false)
                    .obstructedMatchIds(List.of())
                    .nextSchedule(NextSyncSchedule.hours(12, "No upcoming matches - checking twice daily", true))
                    .build();
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

        return MatchSyncResult.builder()
                .seasonId(context.season().getId())
                .roundId(context.round().getId())
                .roundPosition(context.round().getPosition())
                .roundStatus(roundStatus)
                .matchesProcessed(context.matchesProcessed())
                .matchesUpdated(context.matchesUpdated())
                .newlyFinishedMatches(context.finishedMatchIds().size())
                .finishedMatchIds(context.finishedMatchIds())
                .allMatchesComplete(allComplete)
                .hasBlockingMatches(hasBlocking)
                .roundObstructed(roundObstructed)
                .obstructedMatchIds(obstructedMatchIds)
                .nextSchedule(nextSchedule)
                .build();
    }

    private Match findMatchByExternalId(List<Match> matches, String externalId) {
        return matches.stream()
                .filter(m -> m.getClientId() != null && externalId.equals(String.valueOf(m.getClientId())))
                .findFirst()
                .orElse(null);
    }

    private UpdateResult updateMatch(Match existing, FeedMatch apiMatch) {
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

    private record FetchedMatchData(RoundContext roundContext, List<FeedMatch> matches) {}

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
