package com.ligitabl.api.web.predictions.userpredictions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.shared.PredictionAccessMode;
import com.ligitabl.api.rest.prediction.shared.RankingSource;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.domain.user.UserContext;
import com.ligitabl.api.web.shared.error.ErrorMapper;
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
 *   Viewing other user: Returns their prediction as READONLY_VIEWING_OTHER
 *   User not found: Returns fallback as READONLY_USER_NOT_FOUND
 */
@Service
@AllArgsConstructor
public class GetUserPredictionUseCase {
    private final CompetitionDefaults competitionDefaults;
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
        String roundState = resolveRoundState(viewingRoundEntity);
        boolean seasonCompleted = season.isCompleted();

        // Determine access mode and rankings based on user type
        return switch (ctx.userType()) {
            case GUEST -> buildGuestView(
                    query, currentRound, lastRound, viewingRound, isCurrentRound, roundState, seasonCompleted);
            case AUTHENTICATED -> buildAuthenticatedView(
                    query, currentRound, lastRound, viewingRound, isCurrentRound, roundState, seasonCompleted);
            case VIEWING_OTHER -> buildViewingOtherView(
                    query, currentRound, lastRound, viewingRound, isCurrentRound, roundState, seasonCompleted);
            case USER_NOT_FOUND -> buildUserNotFoundView(
                    query, currentRound, lastRound, viewingRound, isCurrentRound, roundState, seasonCompleted);
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
            boolean seasonCompleted) {
        RankingsWithSource rankingsWithSource = getFallbackRankings(qry);

        // Get standings and points - use historical data for past rounds
        Map<String, Integer> standingsMap = isCurrentRound
                ? standingsRepo.findPositionMap(qry.seasonId(), currentRound)
                : standingsRepo.findPositionMap(qry.seasonId(), viewingRound);
        Map<String, Integer> pointsMap = isCurrentRound
                ? standingsRepo.findPointsMap(qry.seasonId(), currentRound)
                : standingsRepo.findPointsMap(qry.seasonId(), viewingRound);

        String message =
                isCurrentRound ? "Sign up to create your prediction" : "Viewing Gameweek " + viewingRound + " results";

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                PredictionAccessMode.READONLY_GUEST,
                null, // swapCooldown not applicable
                isCurrentRound ? getMatches(qry.seasonId(), currentRound) : Map.of(),
                standingsMap,
                pointsMap,
                currentRound,
                lastRound,
                viewingRound,
                null,
                seasonCompleted,
                roundState,
                message,
                null, // no target display name
                null // no round result for guest
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
            boolean seasonCompleted) {
        UserContext ctx = qry.userContext();

        if (ctx.hasContestEntry()) {
            var seasonPrediction = seasonPredictionRepo
                    .findByUserAndSeason(ctx.userId(), qry.seasonId())
                    .orElseThrow(
                            () -> new IllegalStateException("User context indicates prediction exists but not found"));

            // Get swap cooldown for this user
            SwapCooldown swapCooldown = seasonPrediction.getSwapCooldown();

            // For historical rounds, load RoundResult with scored data
            if (!isCurrentRound) {
                var roundResult = roundResultRepo.findByUserAndRound(ctx.userId(), viewingRound);
                if (roundResult.isPresent()) {
                    // Convert RoundResult rankings to TeamRanking for display
                    List<TeamRank> rankings = convertResultRankingsToTeamRankings(roundResult.get());

                    return new UserPredictionViewData(
                            rankings,
                            RankingSource.USER_PREDICTION,
                            PredictionAccessMode.READONLY_COOLDOWN, // Historical is always readonly
                            null, // No swap cooldown for historical
                            Map.of(), // No matches for historical
                            Map.of(), // Standings come from RoundResult
                            Map.of(), // Points not needed for historical
                            currentRound,
                            lastRound,
                            viewingRound,
                            seasonPrediction.getAtRoundNumber(),
                            seasonCompleted,
                            roundState,
                            "Viewing Gameweek " + viewingRound + " results",
                            null,
                            roundResult.get());
                }
            }

            PredictionAccessMode accessMode = determineAccessMode(swapCooldown, isCurrentRound);

            // Get standings and points for current round
            Map<String, Integer> standingsMap = standingsRepo.findPositionMap(qry.seasonId(), currentRound);
            Map<String, Integer> pointsMap = standingsRepo.findPointsMap(qry.seasonId(), currentRound);

            String message = null;
            if (accessMode == PredictionAccessMode.READONLY_COOLDOWN) {
                message = "Swap cooldown active";
            }

            return new UserPredictionViewData(
                    seasonPrediction.getCurrentRankings(),
                    RankingSource.USER_PREDICTION,
                    accessMode,
                    swapCooldown,
                    getMatches(qry.seasonId(), currentRound),
                    standingsMap,
                    pointsMap,
                    currentRound,
                    lastRound,
                    viewingRound,
                    seasonPrediction.getAtRoundNumber(),
                    seasonCompleted,
                    roundState,
                    message,
                    null,
                    null // No round result for current round
                    );
        }

        // User is authenticated but has no prediction - show fallback with CAN_CREATE_ENTRY
        RankingsWithSource rankingsWithSource = getFallbackRankings(qry);

        // For past rounds without prediction, still show historical standings
        Map<String, Integer> standingsMap = isCurrentRound
                ? standingsRepo.findPositionMap(qry.seasonId(), currentRound)
                : standingsRepo.findPositionMap(qry.seasonId(), viewingRound);
        Map<String, Integer> pointsMap = isCurrentRound
                ? standingsRepo.findPointsMap(qry.seasonId(), currentRound)
                : standingsRepo.findPointsMap(qry.seasonId(), viewingRound);

        // Can only create entry in current round
        PredictionAccessMode accessMode =
                isCurrentRound ? PredictionAccessMode.CAN_CREATE_ENTRY : PredictionAccessMode.READONLY_COOLDOWN;

        String message = isCurrentRound
                ? "Arrange teams and submit to join the competition"
                : "Viewing Gameweek " + viewingRound + " results";

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                accessMode,
                null, // no swap cooldown yet
                isCurrentRound ? getMatches(qry.seasonId(), currentRound) : Map.of(),
                standingsMap,
                pointsMap,
                currentRound,
                lastRound,
                viewingRound,
                null,
                seasonCompleted,
                roundState,
                message,
                null,
                null // No round result
                );
    }

    /**
     * Convert RoundResult rankings to TeamRanking list for template display.
     */
    private List<TeamRank> convertResultRankingsToTeamRankings(RoundResult result) {
        return result.getRankings().stream().map(ResultTeamRank::getRanking).toList();
    }

    private List<TeamRank> convertStandingsRankingsToTeamRankings(Standings standings) {
        return standings.getRankings().stream()
                .map(StandingsTeamRank::getRanking)
                .toList();
    }

    /**
     * Build view for viewing another user's predictions.
     */
    private UserPredictionViewData buildViewingOtherView(
            GetUserPredictionQuery qry,
            int currentRound,
            int lastRound,
            int viewingRound,
            boolean isCurrentRound,
            String roundState,
            boolean seasonCompleted) {
        UserContext ctx = qry.userContext();

        // For historical rounds, load RoundResult with scored data
        if (!isCurrentRound && ctx.hasContestEntry()) {
            var roundResult = roundResultRepo.findByUserAndRound(ctx.userId(), viewingRound);
            if (roundResult.isPresent()) {
                List<TeamRank> rankings = convertResultRankingsToTeamRankings(roundResult.get());

                return new UserPredictionViewData(
                        rankings,
                        RankingSource.USER_PREDICTION,
                        PredictionAccessMode.READONLY_VIEWING_OTHER,
                        null,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        currentRound,
                        lastRound,
                        viewingRound,
                        null,
                        seasonCompleted,
                        roundState,
                        "Viewing " + (qry.targetDisplayName() != null ? qry.targetDisplayName() : "user")
                                + "'s Gameweek " + viewingRound + " result",
                        qry.targetDisplayName(),
                        roundResult.get());
            }
        }

        // If target user has a prediction, show it (current round)
        if (ctx.hasContestEntry()) {
            var prediction = seasonPredictionRepo
                    .findByUserAndSeason(ctx.userId(), qry.seasonId())
                    .orElseThrow(
                            () -> new IllegalStateException("User context indicates prediction exists but not found"));

            return new UserPredictionViewData(
                    prediction.getCurrentRankings(),
                    RankingSource.USER_PREDICTION,
                    PredictionAccessMode.READONLY_VIEWING_OTHER,
                    null, // no swap cooldown - readonly
                    getMatches(qry.seasonId(), currentRound),
                    standingsRepo.findPositionMap(qry.seasonId(), currentRound),
                    standingsRepo.findPointsMap(qry.seasonId(), currentRound),
                    currentRound,
                    lastRound,
                    viewingRound,
                    prediction.getAtRoundNumber(),
                    seasonCompleted,
                    roundState,
                    "Viewing " + (qry.targetDisplayName() != null ? qry.targetDisplayName() : "user") + "'s prediction",
                    qry.targetDisplayName(),
                    null);
        }

        // Target user exists but has no prediction - show fallback
        RankingsWithSource rankingsWithSource = getFallbackRankings(qry);

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                PredictionAccessMode.READONLY_VIEWING_OTHER,
                null,
                isCurrentRound ? getMatches(qry.seasonId(), currentRound) : Map.of(),
                isCurrentRound ? standingsRepo.findPositionMap(qry.seasonId(), currentRound) : Map.of(),
                isCurrentRound ? standingsRepo.findPointsMap(qry.seasonId(), currentRound) : Map.of(),
                currentRound,
                lastRound,
                viewingRound,
                null,
                seasonCompleted,
                roundState,
                (qry.targetDisplayName() != null ? qry.targetDisplayName() : "This user")
                        + " hasn't made a prediction yet",
                qry.targetDisplayName(),
                null);
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
            boolean seasonCompleted) {
        RankingsWithSource rankingsWithSource = getFallbackRankings(qry);

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                PredictionAccessMode.READONLY_USER_NOT_FOUND,
                null,
                isCurrentRound ? getMatches(qry.seasonId(), currentRound) : Map.of(),
                isCurrentRound ? standingsRepo.findPositionMap(qry.seasonId(), currentRound) : Map.of(),
                isCurrentRound ? standingsRepo.findPointsMap(qry.seasonId(), currentRound) : Map.of(),
                currentRound,
                lastRound,
                viewingRound,
                null,
                seasonCompleted,
                roundState,
                "User not found",
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

        if (swapCooldown != null && swapCooldown.canSwap(Instant.now())) {
            return PredictionAccessMode.EDITABLE;
        }

        return PredictionAccessMode.READONLY_COOLDOWN;
    }

    /**
     * Get fallback rankings using the three-tier hierarchy:
     * 1. Current round standings
     * 2. Season baseline rankings
     */
    private RankingsWithSource getFallbackRankings(GetUserPredictionQuery query) {
        // Try current round standings first
        var roundStandings = standingsRepo.findLatestBySeason(query.seasonId());
        if (roundStandings.isPresent()) {
            return new RankingsWithSource(
                    RankingSource.ROUND_STANDINGS, convertStandingsRankingsToTeamRankings(roundStandings.get()));
        }

        // Fallback to season baseline (guaranteed to exist)
        var baseline = seasonRepo
                .findById(query.seasonId())
                .map(Season::getInitialRankings)
                .orElseThrow(() -> new IllegalStateException(
                        "Season baseline rankings not found for season: " + query.seasonId()));

        return new RankingsWithSource(RankingSource.SEASON_BASELINE, baseline);
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

    private String resolveRoundState(Round round) {
        if (round == null) {
            return RoundStatus.UNKNOWN.name();
        }

        if (round.isFinalized()) {
            return RoundStatus.FINALIZED.name();
        }

        var matches = matchRepo.findByRoundId(round.getId());
        if (matches == null || matches.isEmpty()) {
            return RoundStatus.OPEN.name();
        }

        return round.computeStatus(matches).name();
    }
}
