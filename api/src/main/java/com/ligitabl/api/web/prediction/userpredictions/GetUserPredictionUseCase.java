package com.ligitabl.api.web.prediction.userpredictions;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.web.shared.command.GetUserPredictionCommand;
import com.ligitabl.api.web.shared.domain.prediction.PredictionAccessMode;
import com.ligitabl.api.web.shared.domain.prediction.RankingSource;
import com.ligitabl.api.web.shared.domain.user.UserContext;
import com.ligitabl.api.web.shared.error.ErrorMapper;
import com.ligitabl.api.web.shared.error.UseCaseError;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Use case for retrieving user predictions with access mode resolution.
 *
 * <p>Handles different user contexts:
 * <ul>
 *   <li><b>Guest</b>: Returns fallback rankings as READONLY_GUEST</li>
 *   <li><b>Authenticated with prediction</b>: Returns user's prediction as EDITABLE or READONLY_COOLDOWN</li>
 *   <li><b>Authenticated without prediction</b>: Returns fallback as CAN_CREATE_ENTRY</li>
 *   <li><b>Viewing other user</b>: Returns their prediction as READONLY_VIEWING_OTHER</li>
 *   <li><b>User not found</b>: Returns fallback as READONLY_USER_NOT_FOUND</li>
 * </ul>
 *
 * <p>This is the Railway-Oriented Programming boundary - domain operations
 * are wrapped in Either monad to provide type-safe error handling.</p>
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
     *
     * @param command the get user prediction command with user context
     * @return Either containing UseCaseError (left) or UserPredictionViewData (right)
     */
    public Either<UseCaseError, UserPredictionViewData> execute(GetUserPredictionCommand command) {
        return Either.catching(
                () -> buildViewData(command),
                ErrorMapper::toUseCaseError
        );
    }

    /**
     * Build complete prediction view data based on user context.
     */
    private UserPredictionViewData buildViewData(GetUserPredictionCommand command) {
        UserContext ctx = command.userContext();
        Season season = getActiveSeason();
        int currentRound = getCurrentRound(season);
        int viewingRound = command.resolveRound(currentRound, season.getMaxRounds());
        boolean isCurrentRound = viewingRound == currentRound;

        // Determine access mode and rankings based on user type
        return switch (ctx.userType()) {
            case GUEST -> buildGuestView(command, currentRound, viewingRound, isCurrentRound);
            case AUTHENTICATED ->
                    buildAuthenticatedView(command, currentRound, viewingRound, isCurrentRound);
            case VIEWING_OTHER ->
                    buildViewingOtherView(command, currentRound, viewingRound, isCurrentRound);
            case USER_NOT_FOUND ->
                    buildUserNotFoundView(command, currentRound, viewingRound, isCurrentRound);
        };
    }

    /**
     * Build view for guest users (not logged in).
     * Always returns fallback rankings with READONLY_GUEST access mode.
     */
    private UserPredictionViewData buildGuestView(
            GetUserPredictionCommand cmd,
            int currentRound,
            int viewingRound,
            boolean isCurrentRound
    ) {
        RankingsWithSource rankingsWithSource = getFallbackRankings(cmd);

        // Get standings and points - use historical data for past rounds
        Map<String, Integer> standingsMap = isCurrentRound
                ? standingsRepo.findPositionMap(cmd.seasonId(), currentRound)
                : standingsRepo.findPositionMap(cmd.seasonId(), viewingRound);
        Map<String, Integer> pointsMap = isCurrentRound
                ? standingsRepo.findPointsMap(cmd.seasonId(), currentRound)
                : standingsRepo.findPointsMap(cmd.seasonId(), viewingRound);

        String message = isCurrentRound
                ? "Log in to create your prediction"
                : "Viewing Gameweek " + viewingRound + " results";

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                PredictionAccessMode.READONLY_GUEST,
                null, // swapCooldown not applicable
                isCurrentRound ? getMatches(cmd.seasonId(), currentRound) : Map.of(),
                standingsMap,
                pointsMap,
                currentRound,
                viewingRound,
                isCurrentRound ? "OPEN" : "COMPLETED",
                message,
                null, // no target display name
                null  // no round result for guest
        );
    }

    /**
     * Build view for authenticated users viewing their own predictions.
     */
    private UserPredictionViewData buildAuthenticatedView(
            GetUserPredictionCommand cmd,
            int currentRound,
            int viewingRound,
            boolean isCurrentRound
    ) {
        UserContext ctx = cmd.userContext();

        if (ctx.hasContestEntry()) {
                var seasonPrediction = seasonPredictionRepo
                    .findByUserAndSeason(ctx.userId(), cmd.seasonId())
                    .orElseThrow(() -> new IllegalStateException(
                        "User context indicates prediction exists but not found"));

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
                            viewingRound,
                            "COMPLETED",
                            "Viewing Gameweek " + viewingRound + " results",
                            null,
                            roundResult.get()
                    );
                }
            }

            PredictionAccessMode accessMode = determineAccessMode(swapCooldown, isCurrentRound);

            // Get standings and points for current round
            Map<String, Integer> standingsMap = standingsRepo.findPositionMap(cmd.seasonId(), currentRound);
            Map<String, Integer> pointsMap = standingsRepo.findPointsMap(cmd.seasonId(), currentRound);

            String message = null;
            if (accessMode == PredictionAccessMode.READONLY_COOLDOWN) {
                message = "Swap cooldown active";
            }

            return new UserPredictionViewData(
                    seasonPrediction.getCurrentRankings(),
                    RankingSource.USER_PREDICTION,
                    accessMode,
                    swapCooldown,
                    getMatches(cmd.seasonId(), currentRound),
                    standingsMap,
                    pointsMap,
                    currentRound,
                    viewingRound,
                    "OPEN",
                    message,
                    null,
                    null // No round result for current round
            );
        }

        // User is authenticated but has no prediction - show fallback with CAN_CREATE_ENTRY
        RankingsWithSource rankingsWithSource = getFallbackRankings(cmd);

        // For past rounds without prediction, still show historical standings
        Map<String, Integer> standingsMap = isCurrentRound
                ? standingsRepo.findPositionMap(cmd.seasonId(), currentRound)
                : standingsRepo.findPositionMap(cmd.seasonId(), viewingRound);
        Map<String, Integer> pointsMap = isCurrentRound
            ? standingsRepo.findPointsMap(cmd.seasonId(), currentRound)
                : standingsRepo.findPointsMap(cmd.seasonId(), viewingRound);

        // Can only create entry in current round
        PredictionAccessMode accessMode = isCurrentRound
                ? PredictionAccessMode.CAN_CREATE_ENTRY
                : PredictionAccessMode.READONLY_COOLDOWN;

        String message = isCurrentRound
                ? "Arrange teams and submit to join the competition"
                : "Viewing Gameweek " + viewingRound + " results";

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                accessMode,
                null, // no swap cooldown yet
                isCurrentRound ? getMatches(cmd.seasonId(), currentRound) : Map.of(),
                standingsMap,
                pointsMap,
                currentRound,
                viewingRound,
                isCurrentRound ? "OPEN" : "COMPLETED",
                message,
                null,
                null // No round result
        );
    }

    /**
     * Convert RoundResult rankings to TeamRanking list for template display.
     */
    private List<TeamRank> convertResultRankingsToTeamRankings(RoundResult result) {
        return result.getRankings().stream()
                .map(ResultTeamRank::getRanking)
                .toList();
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
            GetUserPredictionCommand cmd,
            int currentRound,
            int viewingRound,
            boolean isCurrentRound
    ) {
        UserContext ctx = cmd.userContext();

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
                        viewingRound,
                        "COMPLETED",
                        "Viewing " + (cmd.targetDisplayName() != null ? cmd.targetDisplayName() : "user") + "'s Gameweek " + viewingRound + " result",
                        cmd.targetDisplayName(),
                        roundResult.get()
                );
            }
        }

        // If target user has a prediction, show it (current round)
        if (ctx.hasContestEntry()) {
            var prediction = seasonPredictionRepo
                    .findByUserAndSeason(ctx.userId(), cmd.seasonId())
                    .orElseThrow(() -> new IllegalStateException(
                            "User context indicates prediction exists but not found"
                    ));

            return new UserPredictionViewData(
                    prediction.getCurrentRankings(),
                    RankingSource.USER_PREDICTION,
                    PredictionAccessMode.READONLY_VIEWING_OTHER,
                    null, // no swap cooldown - readonly
                    getMatches(cmd.seasonId(), currentRound),
                    standingsRepo.findPositionMap(cmd.seasonId(), currentRound),
                    standingsRepo.findPointsMap(cmd.seasonId(), currentRound),
                    currentRound,
                    viewingRound,
                    "OPEN",
                    "Viewing " + (cmd.targetDisplayName() != null ? cmd.targetDisplayName() : "user") + "'s prediction",
                    cmd.targetDisplayName(),
                    null
            );
        }

        // Target user exists but has no prediction - show fallback
        RankingsWithSource rankingsWithSource = getFallbackRankings(cmd);

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                PredictionAccessMode.READONLY_VIEWING_OTHER,
                null,
                isCurrentRound ? getMatches(cmd.seasonId(), currentRound) : Map.of(),
                isCurrentRound ? standingsRepo.findPositionMap(cmd.seasonId(), currentRound) : Map.of(),
                isCurrentRound ? standingsRepo.findPointsMap(cmd.seasonId(), currentRound) : Map.of(),
                currentRound,
                viewingRound,
                isCurrentRound ? "OPEN" : "COMPLETED",
                (cmd.targetDisplayName() != null ? cmd.targetDisplayName() : "This user") + " hasn't made a prediction yet",
                cmd.targetDisplayName(),
                null
        );
    }

    /**
     * Build view when the target user was not found.
     */
    private UserPredictionViewData buildUserNotFoundView(
            GetUserPredictionCommand cmd,
            int currentRound,
            int viewingRound,
            boolean isCurrentRound
    ) {
        RankingsWithSource rankingsWithSource = getFallbackRankings(cmd);

        return new UserPredictionViewData(
                rankingsWithSource.rankings(),
                rankingsWithSource.source(),
                PredictionAccessMode.READONLY_USER_NOT_FOUND,
                null,
                isCurrentRound ? getMatches(cmd.seasonId(), currentRound) : Map.of(),
                isCurrentRound ? standingsRepo.findPositionMap(cmd.seasonId(), currentRound) : Map.of(),
                isCurrentRound ? standingsRepo.findPointsMap(cmd.seasonId(), currentRound) : Map.of(),
                currentRound,
                viewingRound,
                isCurrentRound ? "OPEN" : "COMPLETED",
                "User not found",
                null,
                null
        );
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
    private RankingsWithSource getFallbackRankings(GetUserPredictionCommand command) {
        // Try current round standings first
        var roundStandings = standingsRepo.findLatestBySeason(command.seasonId());
        if (roundStandings.isPresent()) {
            return new RankingsWithSource(RankingSource.ROUND_STANDINGS,
                    convertStandingsRankingsToTeamRankings(roundStandings.get()));
        }

        // Fallback to season baseline (guaranteed to exist)
        var baseline = seasonRepo
                .findById(command.seasonId())
                .map(Season::getInitialRankings)
                .orElseThrow(() -> new IllegalStateException(
                        "Season baseline rankings not found for season: " + command.seasonId()
                ));

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
    private record RankingsWithSource(
            RankingSource source,
            List<TeamRank> rankings
    ) {
    }

    /**
     * Complete view data returned by this use case.
     *
     * <p>Contains all information needed by the template to render the prediction view,
     * including access mode for UI control rendering.</p>
     */
    public record UserPredictionViewData(
            List<TeamRank> rankings,
            RankingSource source,
            PredictionAccessMode accessMode,
            SwapCooldown swapCooldown,
            Map<String, List<Match>> matches,
            Map<String, Integer> standingsMap,
            Map<String, Integer> pointsMap,
            int currentRound,
            int viewingRound,
            String roundState,
            String message,
            String targetDisplayName,
            RoundResult roundResult  // Present for historical views with scored results
    ) {
        public UserPredictionViewData {
            Objects.requireNonNull(rankings, "rankings are required");
            Objects.requireNonNull(source, "source is required");
            Objects.requireNonNull(accessMode, "accessMode is required");
            rankings = List.copyOf(rankings);
            matches = matches != null ? Map.copyOf(matches) : Map.of();
            standingsMap = standingsMap != null ? Map.copyOf(standingsMap) : Map.of();
            pointsMap = pointsMap != null ? Map.copyOf(pointsMap) : Map.of();
        }

        /**
         * Check if this view has historical round result data.
         */
        public boolean hasRoundResult() {
            return roundResult != null;
        }

        /**
         * Check if viewing the current round.
         */
        public boolean isCurrentRound() {
            return viewingRound == currentRound;
        }

        /**
         * Check if user can swap teams.
         * Returns true for EDITABLE or CAN_CREATE_ENTRY modes.
         */
        public boolean canSwap() {
            return accessMode == PredictionAccessMode.EDITABLE ||
                    accessMode == PredictionAccessMode.CAN_CREATE_ENTRY;
        }

        /**
         * Check if user can create a new entry (initial prediction).
         */
        public boolean canCreateEntry() {
            return accessMode == PredictionAccessMode.CAN_CREATE_ENTRY;
        }

        /**
         * Check if the view is readonly.
         */
        public boolean isReadonly() {
            return accessMode.isReadonly();
        }

        /**
         * Check if this is a guest user.
         */
        public boolean isGuest() {
            return accessMode == PredictionAccessMode.READONLY_GUEST;
        }

        /**
         * Check if viewing another user's prediction.
         */
        public boolean isViewingOther() {
            return accessMode == PredictionAccessMode.READONLY_VIEWING_OTHER;
        }

        /**
         * Check if target user was not found.
         */
        public boolean isUserNotFound() {
            return accessMode == PredictionAccessMode.READONLY_USER_NOT_FOUND;
        }
    }

    private Season getActiveSeason() {
        return seasonRepo.findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }

    private int getCurrentRound(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            throw new IllegalStateException("Season has no current round");
        }

        return roundRepo.findById(currentRoundId)
                .map(Round::getPosition)
                .orElseThrow(() -> new IllegalStateException("Current round not found"));
    }
}
