package com.ligitabl.api.web.predictions.userpredictions;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.shared.PredictionAccessMode;
import com.ligitabl.api.rest.prediction.shared.RankingSource;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.error.ErrorMapper;
import com.ligitabl.api.web.shared.user.UserContext;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

import lombok.AllArgsConstructor;

/**
 * Use case for retrieving user predictions with access mode resolution.
 *
 * Handles different user contexts:
 *   Guest: Returns fallback rankings as READONLY_GUEST
 *   Authenticated with prediction: Returns user's prediction as EDITABLE or READONLY_COOLDOWN
 *   Authenticated without prediction: Returns fallback as CAN_CREATE_ENTRY
 *   User not found: Returns fallback as READONLY_USER_NOT_FOUND
 */
@Service
@AllArgsConstructor
public class GetUserPredictionUseCase {
    private final CompetitionDefaults competitionDefaults;
    private final CompetitionRepo competitionRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final RoundResultRepo roundResultRepo;
    private final StandingsRepo standingsRepo;
    private final MatchRepo matchRepo;

    /**
     * Execute the use case with user context resolution.
     */
    public Either<UseCaseError, UserPredictionViewData> execute(GetUserPredictionQuery query) {
        return Either.catching(() -> buildViewData(query), ErrorMapper::toUseCaseError);
    }

    /**
     * Build complete prediction view data based on user context.
     */
    private UserPredictionViewData buildViewData(GetUserPredictionQuery query) {
        UserContext ctx = query.userContext();
        Season season = getActiveSeason();
        Round currentRoundEntity = getCurrentRoundEntity(season);
        int currentRound = currentRoundEntity.getPosition();
        int lastRound = season.getMaxRounds();
        int viewingRound = query.resolveRound(currentRound, lastRound);
        boolean isCurrentRound = viewingRound == currentRound;
        Round viewingRoundEntity =
                isCurrentRound ? currentRoundEntity : getRoundByPosition(season.getId(), viewingRound);
        boolean seasonCompleted = season.isCompleted();
        RoundStatus currentRoundStatus = resolveRoundStatus(currentRoundEntity);
        String roundState = isCurrentRound ? currentRoundStatus.name() : resolveRoundState(viewingRoundEntity);

        // Determine access mode and rankings based on user type
        return switch (ctx.userType()) {
            case GUEST -> buildGuestView(
                    query,
                    currentRound,
                    lastRound,
                    viewingRound,
                    isCurrentRound,
                    roundState,
                    seasonCompleted,
                    currentRoundStatus);
            case AUTHENTICATED -> buildAuthenticatedView(
                    query,
                    currentRound,
                    lastRound,
                    viewingRound,
                    isCurrentRound,
                    roundState,
                    seasonCompleted,
                    currentRoundStatus);
            case USER_NOT_FOUND -> buildUserNotFoundView(
                    query,
                    currentRound,
                    lastRound,
                    viewingRound,
                    isCurrentRound,
                    roundState,
                    seasonCompleted,
                    currentRoundStatus);
        };
    }

    /**
     * Build view for guest users (not logged in).
     * Always returns fallback rankings with READONLY_GUEST access mode.
     */
    private UserPredictionViewData buildGuestView(
            GetUserPredictionQuery qry,
            int currentRound,
            int lastRound,
            int viewingRound,
            boolean isCurrentRound,
            String roundState,
            boolean seasonCompleted,
            RoundStatus currentRoundStatus) {
        RankingsWithSource rankingsWithSource = getPreviousRoundRankings(qry.seasonId(), currentRound);

        int standingsRound = isCurrentRound ? currentRound : viewingRound;
        StandingsMaps standingsMaps = getStandingsMaps(qry.seasonId(), standingsRound);
        Map<String, Integer> standingsMap = standingsMaps.positions();
        Map<String, Integer> pointsMap = standingsMaps.points();
        Map<String, Integer> goalDifferenceMap = standingsMaps.goalDifference();

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                PredictionAccessMode.READONLY_GUEST,
                null, // swapCooldown not applicable
                isCurrentRound ? getMatches(qry.seasonId(), currentRound) : Map.of(),
                standingsMap,
                pointsMap,
                goalDifferenceMap,
                currentRound,
                lastRound,
                viewingRound,
                null,
                seasonCompleted,
                roundState,
                null, // no round result for guest
                null, // no swap history for guests
                null,
                null,
                null // no best scores for guests
                );
    }

    /**
     * Build view for authenticated users viewing their own predictions.
     */
    private UserPredictionViewData buildAuthenticatedView(
            GetUserPredictionQuery qry,
            int currentRound,
            int lastRound,
            int viewingRound,
            boolean isCurrentRound,
            String roundState,
            boolean seasonCompleted,
            RoundStatus currentRoundStatus) {
        UserContext ctx = qry.userContext();

        if (ctx.hasContestEntry()) {
            var seasonPrediction = seasonPredictionRepo
                    .findByUserAndSeason(ctx.userId(), qry.seasonId())
                    .orElseThrow(
                            () -> new IllegalStateException("User context indicates prediction exists but not found"));

            // Get swap cooldown for this user
            boolean openingRoundAvailable = seasonPrediction.getOpeningCommittedRound() != currentRound
                    && seasonPrediction.getLastSwapAt() != null
                    && currentRoundStatus == RoundStatus.OPEN;
            SwapCooldown swapCooldown = new SwapCooldown(seasonPrediction.getLastSwapAt(), true, openingRoundAvailable);

            // For historical rounds, load RoundResult with scored data
            if (!isCurrentRound) {
                var roundResult = roundResultRepo.findByUserAndRound(ctx.userId(), viewingRound);
                if (roundResult.isPresent()) {
                    // Convert RoundResult rankings to TeamRanking for display
                    List<TeamRank> rankings = convertResultRankingsToTeamRankings(roundResult.get());

                    Competition competition = competitionRepo
                            .findBySlug(competitionDefaults.defaultCompetitionSlug())
                            .orElseThrow(() -> new IllegalStateException("Competition not found"));

                    RoundSpan sprint = competition.sprintForRound(viewingRound);

                    List<RoundResult> sprintResults = roundResultRepo.findByUserAndSeasonAndRoundPositionRange(
                            ctx.userId(), qry.seasonId(), sprint.getFrom(), viewingRound);
                    int sprintBest = sprintResults.stream()
                            .mapToInt(RoundResult::getTotalScore)
                            .max()
                            .orElse(0);

                    List<RoundResult> seasonResults = roundResultRepo.findByUserAndSeasonAndRoundPositionRange(
                            ctx.userId(), qry.seasonId(), 1, viewingRound);
                    int seasonBest = seasonResults.stream()
                            .mapToInt(RoundResult::getTotalScore)
                            .max()
                            .orElse(0);

                    return new UserPredictionViewData(
                            rankings,
                            RankingSource.USER_PREDICTION,
                            PredictionAccessMode.READONLY_COOLDOWN, // Historical is always readonly
                            null, // No swap cooldown for historical
                            Map.of(), // No matches for historical
                            Map.of(), // Standings come from RoundResult
                            Map.of(), // Points not needed for historical
                            Map.of(), // Goal difference not needed for historical
                            currentRound,
                            lastRound,
                            viewingRound,
                            seasonPrediction.getAtRoundNumber(),
                            seasonCompleted,
                            roundState,
                            roundResult.get(),
                            swapsForRound(seasonPrediction, viewingRound),
                            seasonBest,
                            sprintBest,
                            sprint.getName());
                }
            }

            PredictionAccessMode accessMode = determineAccessMode(swapCooldown, isCurrentRound);

            // Get standings and points for current round
            StandingsMaps standingsMaps = getStandingsMaps(qry.seasonId(), currentRound);
            Map<String, Integer> standingsMap = standingsMaps.positions();
            Map<String, Integer> pointsMap = standingsMaps.points();
            Map<String, Integer> goalDifferenceMap = standingsMaps.goalDifference();

            return new UserPredictionViewData(
                    seasonPrediction.getCurrentRankings(),
                    RankingSource.USER_PREDICTION,
                    accessMode,
                    swapCooldown,
                    getMatches(qry.seasonId(), currentRound),
                    standingsMap,
                    pointsMap,
                    goalDifferenceMap,
                    currentRound,
                    lastRound,
                    viewingRound,
                    seasonPrediction.getAtRoundNumber(),
                    seasonCompleted,
                    roundState,
                    null, // No round result for current round
                    swapsForRound(seasonPrediction, viewingRound),
                    null,
                    null,
                    null);
        }

        // User is authenticated but has no prediction - show fallback with CAN_CREATE_ENTRY
        // For past rounds without prediction, still show historical standings
        int standingsRound = isCurrentRound ? currentRound : viewingRound;
        StandingsMaps standingsMaps = getStandingsMaps(qry.seasonId(), standingsRound);
        Map<String, Integer> standingsMap = standingsMaps.positions();
        Map<String, Integer> pointsMap = standingsMaps.points();
        Map<String, Integer> goalDifferenceMap = standingsMaps.goalDifference();

        // Determine if user can create entry and compute atRoundNumber
        // Same logic as CreatePredictionUseCase.determineAtRoundNumber
        PredictionAccessMode accessMode;
        Integer atRoundNumber = null;

        if (!isCurrentRound) {
            accessMode = PredictionAccessMode.READONLY_COOLDOWN;
        } else if (currentRound == lastRound && currentRoundStatus != RoundStatus.OPEN) {
            // Last round and not open - season ending, can't join
            accessMode = PredictionAccessMode.READONLY_COOLDOWN;
        } else {
            accessMode = PredictionAccessMode.CAN_CREATE_ENTRY;
            atRoundNumber = currentRoundStatus == RoundStatus.OPEN ? currentRound : currentRound + 1;
        }

        RankingsWithSource rankingsWithSource = getPreviousRoundRankings(qry.seasonId(), currentRound);

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                accessMode,
                null, // no swap cooldown yet
                isCurrentRound ? getMatches(qry.seasonId(), currentRound) : Map.of(),
                standingsMap,
                pointsMap,
                goalDifferenceMap,
                currentRound,
                lastRound,
                viewingRound,
                atRoundNumber,
                seasonCompleted,
                roundState,
                null, // No round result
                null, // No swap history — user has no prediction yet
                null,
                null,
                null);
    }

    /**
     * Convert RoundResult rankings to TeamRanking list for template display.
     */
    private List<TeamRank> convertResultRankingsToTeamRankings(RoundResult result) {
        return result.getRankings().stream().map(ResultTeamRank::getRanking).toList();
    }

    /**
     * Build view when the target user was not found.
     */
    private UserPredictionViewData buildUserNotFoundView(
            GetUserPredictionQuery qry,
            int currentRound,
            int lastRound,
            int viewingRound,
            boolean isCurrentRound,
            String roundState,
            boolean seasonCompleted,
            RoundStatus currentRoundStatus) {
        RankingsWithSource rankingsWithSource = getPreviousRoundRankings(qry.seasonId(), currentRound);

        StandingsMaps currentStandingsMaps =
                isCurrentRound ? getStandingsMaps(qry.seasonId(), currentRound) : StandingsMaps.empty();

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                PredictionAccessMode.READONLY_USER_NOT_FOUND,
                null,
                isCurrentRound ? getMatches(qry.seasonId(), currentRound) : Map.of(),
                currentStandingsMaps.positions(),
                currentStandingsMaps.points(),
                currentStandingsMaps.goalDifference(),
                currentRound,
                lastRound,
                viewingRound,
                null,
                seasonCompleted,
                roundState,
                null,
                null, // swap history not applicable
                null,
                null,
                null);
    }

    /**
     * Determine access mode based on swap cooldown status.
     */
    private PredictionAccessMode determineAccessMode(SwapCooldown swapCooldown, boolean isCurrentRound) {
        if (!isCurrentRound) {
            return PredictionAccessMode.READONLY_COOLDOWN; // Historical rounds are always readonly
        }

        if (swapCooldown != null && (swapCooldown.canSwap(Instant.now()) || swapCooldown.openingRoundAvailable())) {
            return PredictionAccessMode.EDITABLE;
        }

        return PredictionAccessMode.READONLY_COOLDOWN;
    }

    private StandingsMaps getStandingsMaps(UUID seasonId, int roundPosition) {
        var standingsOpt = standingsRepo.findBySeasonAndRoundPosition(seasonId, roundPosition);
        if (standingsOpt.isEmpty() || standingsOpt.get().getRankings() == null) {
            return StandingsMaps.empty();
        }

        Map<String, Integer> positions = new HashMap<>();
        Map<String, Integer> points = new HashMap<>();
        Map<String, Integer> goalDifference = new HashMap<>();

        for (var rank : standingsOpt.get().getRankings()) {
            if (rank == null || rank.getRanking() == null) {
                continue;
            }
            String code = rank.getRanking().getCode();
            if (code == null) {
                continue;
            }
            positions.put(code, rank.getRanking().getPosition());
            if (rank.getMetadata() != null) {
                points.put(code, rank.getMetadata().getPoints());
                goalDifference.put(code, rank.getMetadata().getGd());
            }
        }

        return new StandingsMaps(positions, points, goalDifference);
    }

    private record StandingsMaps(
            Map<String, Integer> positions, Map<String, Integer> points, Map<String, Integer> goalDifference) {
        private static StandingsMaps empty() {
            return new StandingsMaps(Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * Get previous round standings as fallback for users without a prediction.
     *
     * Always uses currentRound - 2, giving users contrast to help decide where to move teams.
     * Falls back to season baseline when currentRound < 3 (GW1/GW2) or standings unavailable.
     *
     * GW5 → GW3, GW3 → GW1, GW2/GW1 → season baseline.
     */
    private RankingsWithSource getPreviousRoundRankings(UUID seasonId, int currentRound) {
        if (currentRound < 3) {
            return getSeasonBaselineRankings(seasonId);
        }

        var roundStandings = standingsRepo.findBySeasonAndRoundPosition(seasonId, currentRound - 2);
        if (roundStandings.isEmpty()) {
            return getSeasonBaselineRankings(seasonId);
        }

        return new RankingsWithSource(
                RankingSource.PREVIOUS_ROUND_STANDINGS, convertStandingsRankingsToTeamRankings(roundStandings.get()));
    }

    private List<TeamRank> convertStandingsRankingsToTeamRankings(Standings standings) {
        return standings.getRankings().stream()
                .map(StandingsTeamRank::getRanking)
                .toList();
    }

    /**
     * Get season baseline rankings — the shared starting point for all users.
     */
    private RankingsWithSource getSeasonBaselineRankings(UUID seasonId) {
        var baseline = seasonRepo
                .findById(seasonId)
                .map(Season::getInitialRankings)
                .orElseThrow(
                        () -> new IllegalStateException("Season baseline rankings not found for season: " + seasonId));

        return new RankingsWithSource(RankingSource.SEASON_BASELINE, baseline);
    }

    /**
     * Extract swap changes for a specific round from the prediction's swap history.
     */
    private List<SwapChange> swapsForRound(SeasonPrediction prediction, int round) {
        return prediction.getSwaps().stream()
                .filter(rs -> rs.getRound() == round)
                .findFirst()
                .map(RoundSwap::getChanges)
                .orElse(List.of());
    }

    /**
     * Get matches for a round.
     */
    private Map<String, List<Match>> getMatches(UUID seasonId, int round) {
        return matchRepo.findBySeasonAndRound(seasonId, round);
    }

    /**
     * Internal record for rankings with source.
     */
    private record RankingsWithSource(RankingSource source, List<TeamRank> rankings) {}

    private Season getActiveSeason() {
        return seasonRepo
                .findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }

    private Round getCurrentRoundEntity(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            throw new IllegalStateException("Season has no current round");
        }

        return roundRepo
                .findById(currentRoundId)
                .orElseThrow(() -> new IllegalStateException("Current round not found"));
    }

    private Round getRoundByPosition(UUID seasonId, int position) {
        return roundRepo.findBySeasonIdAndPosition(seasonId, position).orElse(null);
    }

    private RoundStatus resolveRoundStatus(Round round) {
        if (round == null) {
            return RoundStatus.UNKNOWN;
        }
        if (round.isFinalized()) {
            return RoundStatus.FINALIZED;
        }
        var matches = matchRepo.findByRoundId(round.getId());
        if (matches == null || matches.isEmpty()) {
            return RoundStatus.OPEN;
        }
        return round.computeStatus(matches);
    }

    private String resolveRoundState(Round round) {
        return resolveRoundStatus(round).name();
    }
}
