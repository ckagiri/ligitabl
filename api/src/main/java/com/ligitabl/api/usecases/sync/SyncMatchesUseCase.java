package com.ligitabl.api.usecases.sync;

import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.client.footballdata.MatchDto;
import com.ligitabl.api.client.footballdata.Score;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.scheduling.AsyncStandingsService;
import com.ligitabl.api.scheduling.sync.MatchSyncResult;
import com.ligitabl.api.scheduling.sync.NextSyncSchedule;
import com.ligitabl.api.scheduling.sync.SyncFrequencyCalculator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncMatchesUseCase {

    private final FootballDataClient footballDataClient; // Port interface (not implementation!)
    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final AsyncStandingsService standingsService;
    private final CompetitionDefaults competitionDefaults;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    public record SyncMatchesCommand() {
    }

    public sealed interface SyncMatchesError {
        record CompetitionNotFound(String code) implements SyncMatchesError {
        }

        record SeasonNotFound(String competitionCode) implements SyncMatchesError {
        }

        record RoundNotFound(UUID roundId) implements SyncMatchesError {
        }

        record DataAccessError(FootballDataClient.ApiError error) implements SyncMatchesError {
        }
    }

    @Transactional
    public Either<SyncMatchesError, MatchSyncResult> execute(SyncMatchesCommand command) {
        log.info("Starting match synchronization for competition: {}", competitionCode);

        return getActiveSeason()
                .flatMap(this::getCurrentRound)
                .flatMap(this::determineAndFetchMatches) // Smart endpoint selection
                .flatMap(this::updateMatches)
                .flatMap(this::recalculateStandings)
                .map(this::calculateNextSync);
    }

    private Either<SyncMatchesError, Season> getActiveSeason() {
        var competitionSlug = competitionDefaults.defaultCompetitionSlug();

        if (competitionRepo.findBySlug(competitionSlug).isEmpty()) {
            return Either.left(new SyncMatchesError.CompetitionNotFound(competitionCode));
        }

        return seasonRepo.findActiveSeason(competitionSlug)
                .map(Either::<SyncMatchesError, Season>right)
                .orElseGet(() -> Either.left(new SyncMatchesError.SeasonNotFound(competitionCode)));
    }

    private Either<SyncMatchesError, RoundContext> getCurrentRound(Season season) {
        return roundRepo.findById(season.getCurrentRoundId())
                .map(round -> {
                    var existingMatches = matchRepo.findByRoundId(round.getId());
                    return Either.<SyncMatchesError, RoundContext>right(
                            new RoundContext(season, round, existingMatches)
                    );
                })
                .orElse(Either.left(new SyncMatchesError.RoundNotFound(
                        season.getCurrentRoundId()
                )));
    }

    /**
     * SMART ENDPOINT SELECTION
     * <p>
     * Determines which API endpoint to use based on current match state.
     * This is the optimization strategy we discussed!
     */
    private Either<SyncMatchesError, FetchedMatchData> determineAndFetchMatches(
            RoundContext context) {

        var existingMatches = context.existingMatches();

        // Strategy 1: Check for LIVE matches first
        boolean hasLive = existingMatches.stream()
                .anyMatch(m -> m.getStatus() == MatchStatus.LIVE);

        if (hasLive) {
            log.debug("Live matches detected - using LIVE endpoint");
            return fetchLiveMatches(context);
        }

        // Strategy 2: Check for imminent/soon kickoffs (today only)
        var nextKickoff = existingMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                .map(Match::getKickOff)
                .filter(k -> k != null)
                .min(OffsetDateTime::compareTo);

        if (nextKickoff.isPresent()) {
            var minutesUntilKickoff = Duration.between(
                    OffsetDateTime.now(),
                    nextKickoff.get()
            ).toMinutes();

            // Imminent (≤10 min) or Soon (≤60 min) - check TODAY only
            if (minutesUntilKickoff <= 60) {
                log.debug("Kickoff in {} minutes - using TODAY endpoint", minutesUntilKickoff);
                return fetchTodayMatches(context);
            }

            // Later today (< 6 hours) - still check TODAY only
            if (minutesUntilKickoff < 360) {
                log.debug("Kickoff in {} hours - using TODAY endpoint", minutesUntilKickoff / 60);
                return fetchTodayMatches(context);
            }
        }

        // Strategy 3: Default - check today + tomorrow (lookahead)
        log.debug("No imminent matches - using DATE RANGE endpoint (today + tomorrow)");
        return fetchUpcomingMatches(context);
    }

    /**
     * Fetch via GET /matches?status=LIVE
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchLiveMatches(RoundContext context) {
        return footballDataClient.getLiveMatches(competitionCode)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    /**
     * Fetch via GET /matches?date=today
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchTodayMatches(RoundContext context) {
        var today = LocalDate.now();

        return footballDataClient.getMatchesForDate(competitionCode, today)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    /**
     * Fetch via GET /matches?dateFrom=today&dateTo=tomorrow
     */
    private Either<SyncMatchesError, FetchedMatchData> fetchUpcomingMatches(RoundContext context) {
        var today = LocalDate.now();
        var dayAfterTomorrow = today.plusDays(2);

        return footballDataClient.getMatchesInDateRange(competitionCode, today, dayAfterTomorrow)
                .mapLeft(error -> (SyncMatchesError) new SyncMatchesError.DataAccessError(error))
                .map(response -> new FetchedMatchData(context, response.matches()));
    }

    private Either<SyncMatchesError, SyncContext> updateMatches(FetchedMatchData fetchedData) {
        var context = fetchedData.roundContext();
        var apiMatches = fetchedData.matches();
        var existingMatches = context.existingMatches();

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
                }
            }
        }

        log.info("Matches processed: {}, updated: {}, newly finished: {}",
                apiMatches.size(), matchesUpdated, finishedMatchIds.size());

        // Reload to get updated status
        var updatedMatches = matchRepo.findByRoundId(context.round().getId());

        return Either.right(new SyncContext(
                context.season(),
                context.round(),
                apiMatches.size(),
                matchesUpdated,
                finishedMatchIds,
                updatedMatches
        ));
    }

    private Either<SyncMatchesError, SyncContext> recalculateStandings(SyncContext context) {
        if (context.finishedMatchIds().isEmpty()) {
            return Either.right(context);
        }

        // Async - don't wait
        standingsService.recalculateAsync(
                context.season().getId(),
                context.round().getPosition()
        );

        return Either.right(context);
    }

    private MatchSyncResult calculateNextSync(SyncContext context) {
        var matches = context.updatedMatches();

        boolean allComplete = matches.stream().allMatch(m ->
                m.getStatus() == MatchStatus.FINISHED ||
                        m.getStatus() == MatchStatus.POSTPONED ||
                        m.getStatus() == MatchStatus.SUSPENDED ||
                        m.getStatus() == MatchStatus.CANCELLED
        );

        boolean hasBlocking = matches.stream().anyMatch(m ->
                m.getStatus() == MatchStatus.CANCELLED ||
                        m.getStatus() == MatchStatus.SUSPENDED
        );

        // Check if no upcoming matches (empty result)
        if (context.matchesProcessed() == 0) {
            return new MatchSyncResult(
                    0, 0, 0,
                    List.of(),
                    false,
                    false,
                    NextSyncSchedule.hours(12, "No upcoming matches - checking twice daily")
            );
        }

        boolean hasLive = matches.stream()
                .anyMatch(m -> m.getStatus() == MatchStatus.LIVE);

        boolean hasScheduled = matches.stream()
                .anyMatch(m -> m.getStatus() == MatchStatus.SCHEDULED);

        var nextKickoff = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                .map(Match::getKickOff)
                .filter(k -> k != null)
                .min(OffsetDateTime::compareTo)
                .orElse(null);

        var nextSchedule = SyncFrequencyCalculator.calculateNextSync(
                hasLive,
                hasScheduled,
                nextKickoff,
                allComplete
        );

        return new MatchSyncResult(
                context.matchesProcessed(),
                context.matchesUpdated(),
                context.finishedMatchIds().size(),
                context.finishedMatchIds(),
                allComplete,
                hasBlocking,
                nextSchedule
        );
    }

    private Match findMatchByExternalId(List<Match> matches, String externalId) {
        return matches.stream()
                .filter(m -> m.getClientId() != null && externalId.equals(String.valueOf(m.getClientId())))
                .findFirst()
                .orElse(null);
    }

    private UpdateResult updateMatch(Match existing, MatchDto apiMatch) {
        var previousStatus = existing.getStatus();
        var newStatus = mapToDomainStatus(apiMatch.status());

        boolean hasChanged = previousStatus != newStatus ||
                scoreChanged(existing, apiMatch.score());

        if (!hasChanged) {
            return new UpdateResult(existing, false, false);
        }

        boolean becameFinished = previousStatus != MatchStatus.FINISHED &&
            newStatus == MatchStatus.FINISHED;

        existing.setStatus(newStatus);
        existing.setKickOff(apiMatch.utcDate());
        if (apiMatch.matchday() != null) {
            existing.setMatchday(apiMatch.matchday());
        }

        return new UpdateResult(existing, hasChanged, becameFinished);
    }

    private boolean scoreChanged(Match existing, Score apiScore) {
        // Implementation depends on your Match entity
        return false; // Placeholder
    }

    private MatchStatus mapToDomainStatus(
            String matchStatus) {
        return switch (matchStatus) {
            case "SCHEDULED", "TIMED" -> MatchStatus.SCHEDULED;
            case "IN_PLAY", "PAUSED" -> MatchStatus.LIVE;
            case "FINISHED", "AWARDED" -> MatchStatus.FINISHED;
            case "SUSPENDED" -> MatchStatus.SUSPENDED;
            case "POSTPONED" -> MatchStatus.POSTPONED;
            case "CANCELLED" -> MatchStatus.CANCELLED;
            default -> {
                log.warn("Unknown match status from API: {}", matchStatus);
                yield MatchStatus.SCHEDULED;
            }
        };
    }

    // Helper records
    private record RoundContext(
            Season season,
            Round round,
            List<Match> existingMatches
    ) {
    }

    private record FetchedMatchData(
            RoundContext roundContext,
            List<MatchDto> matches
    ) {
    }

    private record SyncContext(
            Season season,
            Round round,
            int matchesProcessed,
            int matchesUpdated,
            List<UUID> finishedMatchIds,
            List<Match> updatedMatches
    ) {
    }

    private record UpdateResult(
            Match match,
            boolean hasChanged,
            boolean becameFinished
    ) {
    }
}

