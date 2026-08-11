package com.ligitabl.api.web.predictions.userpredictions;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 *   Guest: Returns fallback rankings as READONLY
 *   Authenticated with prediction: Returns user's prediction as EDITABLE or READONLY
 *   Authenticated without prediction: Returns fallback as CAN_CREATE_ENTRY
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
    private final EntryRepo entryRepo;
    private final PreviewRankingsSupport previewRankingsSupport;
    private final Clock clock;

    /**
     * Execute the use case with user context resolution.
     */
    public Either<UseCaseError, UserPredictionViewData> execute(GetUserPredictionQuery query) {
        return Either.catching(() -> buildViewData(query), ErrorMapper::toUseCaseError);
    }

    /**
     * Request-scoped values shared by every view-builder branch, resolved once per request.
     */
    private record RequestContext(
            int currentRound,
            int lastRound,
            int viewingRound,
            boolean isCurrentRound,
            String roundState,
            boolean seasonCompleted,
            RoundStatus currentRoundStatus,
            Map<String, List<Match>> currentRoundMatches,
            UUID mainContestId) {}

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

        Map<String, List<Match>> currentRoundMatches = getMatches(season.getId(), currentRound);
        RoundStatus currentRoundStatus = resolveRoundStatus(currentRoundEntity, currentRoundMatches);
        String roundState = isCurrentRound ? currentRoundStatus.name() : resolveRoundState(viewingRoundEntity);

        // The season completion flag is no longer an automatic side effect of the last round
        // advancing (RoundAdvancementService leaves season.completed for an admin to set
        // explicitly) — derive the UI-facing "season is done" signal from round state instead.
        boolean seasonCompleted = currentRoundStatus == RoundStatus.ADVANCED && currentRound == lastRound;

        RequestContext rc = new RequestContext(
                currentRound,
                lastRound,
                viewingRound,
                isCurrentRound,
                roundState,
                seasonCompleted,
                currentRoundStatus,
                currentRoundMatches,
                season.getMainContestId());

        return switch (ctx.userType()) {
            case GUEST -> buildGuestView(query, rc);
            case AUTHENTICATED -> buildAuthenticatedView(query, rc);
        };
    }

    /**
     * Build view for guest users (not logged in).
     * Always returns fallback rankings with READONLY access mode.
     */
    private UserPredictionViewData buildGuestView(GetUserPredictionQuery qry, RequestContext rc) {
        PreviewRankingsSupport.RankingsWithSource rankingsWithSource =
                previewRankingsSupport.getPreviousRoundRankings(qry.seasonId(), rc.currentRound());

        int standingsRound = rc.isCurrentRound() ? rc.currentRound() : rc.viewingRound();
        StandingsMaps standingsMaps = getStandingsMaps(qry.seasonId(), standingsRound);

        return UserPredictionViewData.builder()
                .rankings(rankingsWithSource.rankings())
                .source(rankingsWithSource.source())
                .accessMode(PredictionAccessMode.READONLY)
                .matches(rc.isCurrentRound() ? rc.currentRoundMatches() : Map.of())
                .standingsMap(standingsMaps.positions())
                .pointsMap(standingsMaps.points())
                .goalDifferenceMap(standingsMaps.goalDifference())
                .currentRound(rc.currentRound())
                .lastRound(rc.lastRound())
                .viewingRound(rc.viewingRound())
                .seasonCompleted(rc.seasonCompleted())
                .roundState(rc.roundState())
                .isGuest(true)
                .build();
    }

    /**
     * Build view for authenticated users viewing their own predictions.
     *
     * <p>Dispatches to one of three shapes: no prediction yet (fallback + can-create-entry),
     * a historical/scored round (own prediction, but genuinely past or the season's last round
     * once it has advanced), or the current, still-editable round.</p>
     */
    private UserPredictionViewData buildAuthenticatedView(GetUserPredictionQuery qry, RequestContext rc) {
        UserContext ctx = qry.userContext();

        if (!ctx.hasMainContestEntry()) {
            return buildNoPredictionFallbackView(qry, rc);
        }

        var seasonPrediction = seasonPredictionRepo
                .findByUserAndSeason(ctx.userId(), qry.seasonId())
                .orElseThrow(() -> new IllegalStateException("User context indicates prediction exists but not found"));

        // SeasonPrediction.atRoundNumber moves with swaps (it marks the round currentRankings
        // belong to), so the stable "when did this user join" marker for round navigation is the
        // main-contest Entry's joinedAtRound instead.
        Integer joinedAtRound = rc.mainContestId() == null
                ? null
                : entryRepo
                        .findByUserAndContest(ctx.userId(), rc.mainContestId())
                        .map(entry -> Math.max(1, entry.getJoinedAtRound()))
                        .orElse(null);

        // Once the season's last round has advanced, it stays "current" (currentRoundId never
        // moves further), but must be rendered like a historical/scored round, not a live one.
        boolean showAsHistorical = !rc.isCurrentRound()
                || (rc.currentRound() == rc.lastRound() && rc.currentRoundStatus() == RoundStatus.ADVANCED);

        return showAsHistorical
                ? buildHistoricalResultView(qry, rc, ctx, seasonPrediction, joinedAtRound)
                : buildCurrentEditableView(qry, rc, seasonPrediction, joinedAtRound);
    }

    /**
     * Build view for a scored round — genuinely historical, or the current round once it's the
     * season's last round and has advanced.
     */
    private UserPredictionViewData buildHistoricalResultView(
            GetUserPredictionQuery qry,
            RequestContext rc,
            UserContext ctx,
            SeasonPrediction seasonPrediction,
            Integer joinedAtRound) {
        RoundResult roundResult = roundResultRepo
                .findByUserAndRound(ctx.userId(), rc.viewingRound())
                .orElseThrow(() -> new IllegalStateException("Expected RoundResult for user " + ctx.userId()
                        + " at round " + rc.viewingRound() + " but none found (isCurrentRound=" + rc.isCurrentRound()
                        + ", roundStatus=" + rc.currentRoundStatus() + ")"));

        List<TeamRank> rankings = convertResultRankingsToTeamRankings(roundResult);

        Competition competition = competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("Competition not found"));

        RoundSpan sprint = competition.sprintForRound(rc.viewingRound());

        List<RoundResult> sprintResults = roundResultRepo.findByUserAndSeasonAndRoundPositionRange(
                ctx.userId(), qry.seasonId(), sprint.getFrom(), rc.viewingRound());
        int sprintBest = sprintResults.stream()
                .mapToInt(RoundResult::getTotalScore)
                .max()
                .orElse(0);

        List<RoundResult> seasonResults = roundResultRepo.findByUserAndSeasonAndRoundPositionRange(
                ctx.userId(), qry.seasonId(), 1, rc.viewingRound());
        int seasonBest = seasonResults.stream()
                .mapToInt(RoundResult::getTotalScore)
                .max()
                .orElse(0);

        return UserPredictionViewData.builder()
                .rankings(rankings)
                .source(RankingSource.USER_PREDICTION)
                .accessMode(PredictionAccessMode.READONLY) // Historical is always readonly
                .matches(Map.of()) // No matches for historical
                .standingsMap(Map.of()) // Standings come from RoundResult
                .pointsMap(Map.of())
                .goalDifferenceMap(Map.of())
                .currentRound(rc.currentRound())
                .lastRound(rc.lastRound())
                .viewingRound(rc.viewingRound())
                .atRoundNumber(seasonPrediction.getAtRoundNumber())
                .joinedAtRound(joinedAtRound)
                .seasonCompleted(rc.seasonCompleted())
                .roundState(rc.roundState())
                .roundResult(roundResult)
                .roundSwapHistory(swapsForRound(seasonPrediction, rc.viewingRound()))
                .seasonBestScore(seasonBest)
                .sprintBestScore(sprintBest)
                .sprintLabel(sprint.getName())
                .isGuest(false)
                .build();
    }

    /**
     * Build view for the current, still-editable round — the user's live prediction.
     */
    private UserPredictionViewData buildCurrentEditableView(
            GetUserPredictionQuery qry, RequestContext rc, SeasonPrediction seasonPrediction, Integer joinedAtRound) {
        // A still-unmerged pre-season "easter egg" registration (atRoundNumber == 0) must behave
        // exactly like a brand-new predictor once the season starts — any swap made while it was
        // just a pre-season row must not count toward the real opening-round bonus or first-swap-bonus messaging.
        SwapCooldown swapCooldown;
        if (seasonPrediction.isPreSeasonRegistration()) {
            swapCooldown = SwapCooldown.initial();
        } else {
            boolean openingRoundAvailable = seasonPrediction.getOpeningCommittedRound() > 0
                    && seasonPrediction.getOpeningCommittedRound() != rc.currentRound()
                    && seasonPrediction.getLastSwapAt() != null
                    && rc.currentRoundStatus() == RoundStatus.OPEN;
            swapCooldown = new SwapCooldown(seasonPrediction.getLastSwapAt(), true, openingRoundAvailable);
        }
        // Read once and hand downstream. The controller derives the cooldown banner from the same
        // SwapCooldown this access mode was decided from, so a second read there could disagree
        // with this one across the 24-hour boundary — a page telling the user they may swap while
        // the table it renders is read-only, or the reverse.
        Instant evaluatedAt = clock.instant();
        PredictionAccessMode accessMode = determineAccessMode(swapCooldown, rc.isCurrentRound(), evaluatedAt);

        StandingsMaps standingsMaps = getStandingsMaps(qry.seasonId(), rc.currentRound());

        return UserPredictionViewData.builder()
                .rankings(seasonPrediction.getCurrentRankings())
                .source(RankingSource.USER_PREDICTION)
                .accessMode(accessMode)
                .swapCooldown(swapCooldown)
                .accessModeEvaluatedAt(evaluatedAt)
                .matches(rc.currentRoundMatches())
                .standingsMap(standingsMaps.positions())
                .pointsMap(standingsMaps.points())
                .goalDifferenceMap(standingsMaps.goalDifference())
                .currentRound(rc.currentRound())
                .lastRound(rc.lastRound())
                .viewingRound(rc.viewingRound())
                .atRoundNumber(seasonPrediction.getAtRoundNumber())
                .joinedAtRound(joinedAtRound)
                .seasonCompleted(rc.seasonCompleted())
                .roundState(rc.roundState())
                .roundSwapHistory(swapsForRound(seasonPrediction, rc.viewingRound()))
                .isGuest(false)
                .hasPreSeasonRegistration(seasonPrediction.isPreSeasonRegistration())
                .build();
    }

    /**
     * Build view for an authenticated user with no prediction yet — fallback rankings, and
     * CAN_CREATE_ENTRY when the current round is still open to join.
     */
    private UserPredictionViewData buildNoPredictionFallbackView(GetUserPredictionQuery qry, RequestContext rc) {
        int standingsRound = rc.isCurrentRound() ? rc.currentRound() : rc.viewingRound();
        StandingsMaps standingsMaps = getStandingsMaps(qry.seasonId(), standingsRound);

        // Same logic as CreatePredictionUseCase.determineAtRoundNumber
        PredictionAccessMode accessMode;
        Integer atRoundNumber = null;

        if (!rc.isCurrentRound()) {
            accessMode = PredictionAccessMode.READONLY;
        } else if (rc.currentRound() == rc.lastRound() && rc.currentRoundStatus() != RoundStatus.OPEN) {
            // Last round and not open - season ending, can't join
            accessMode = PredictionAccessMode.READONLY;
        } else {
            accessMode = PredictionAccessMode.CAN_CREATE_ENTRY;
            atRoundNumber = rc.currentRoundStatus() == RoundStatus.OPEN ? rc.currentRound() : rc.currentRound() + 1;
        }

        PreviewRankingsSupport.RankingsWithSource rankingsWithSource =
                previewRankingsSupport.getPreviousRoundRankings(qry.seasonId(), rc.currentRound());

        return UserPredictionViewData.builder()
                .rankings(rankingsWithSource.rankings())
                .source(rankingsWithSource.source())
                .accessMode(accessMode)
                .matches(rc.isCurrentRound() ? rc.currentRoundMatches() : Map.of())
                .standingsMap(standingsMaps.positions())
                .pointsMap(standingsMaps.points())
                .goalDifferenceMap(standingsMaps.goalDifference())
                .currentRound(rc.currentRound())
                .lastRound(rc.lastRound())
                .viewingRound(rc.viewingRound())
                .atRoundNumber(atRoundNumber)
                .seasonCompleted(rc.seasonCompleted())
                .roundState(rc.roundState())
                .isGuest(false)
                .build();
    }

    /**
     * Convert RoundResult rankings to TeamRanking list for template display.
     */
    private List<TeamRank> convertResultRankingsToTeamRankings(RoundResult result) {
        return result.getRankings().stream().map(ResultTeamRank::getRanking).toList();
    }

    /**
     * Determine access mode based on swap cooldown status.
     */
    private PredictionAccessMode determineAccessMode(SwapCooldown swapCooldown, boolean isCurrentRound, Instant at) {
        if (!isCurrentRound) {
            return PredictionAccessMode.READONLY; // Historical rounds are always readonly
        }

        if (swapCooldown != null && (swapCooldown.canSwap(at) || swapCooldown.openingRoundAvailable())) {
            return PredictionAccessMode.EDITABLE;
        }

        return PredictionAccessMode.READONLY;
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

    private Season getActiveSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
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

    /**
     * Resolve a round's status from an already-fetched matches map (avoids re-querying matches
     * for the same round the caller already fetched for the view payload).
     *
     * <p>{@code matchesByTeam} is keyed by team code, so every match appears under both its home
     * and away team's list — dedupe by id, not equality: {@link Match}'s Lombok-generated
     * equals/hashCode call {@code getHomeTeam()}/{@code getAwayTeam()}, which throw when those
     * transient, repository-populated fields aren't loaded (as here — this map comes from a
     * plain, team-less finder).</p>
     */
    private RoundStatus resolveRoundStatus(Round round, Map<String, List<Match>> matchesByTeam) {
        Map<UUID, Match> byId = new LinkedHashMap<>();
        matchesByTeam.values().stream().flatMap(List::stream).forEach(match -> byId.putIfAbsent(match.getId(), match));
        return statusForRound(round, List.copyOf(byId.values()));
    }

    private String resolveRoundState(Round round) {
        List<Match> matches = round == null ? List.of() : matchRepo.findByRoundId(round.getId());
        return statusForRound(round, matches).name();
    }

    /**
     * Shared status precedence: advanced rounds are always ADVANCED (even though a round is
     * always finalized before it's advanced), then finalized, then match-based.
     */
    private RoundStatus statusForRound(Round round, List<Match> matches) {
        if (round == null) {
            return RoundStatus.UNKNOWN;
        }
        if (round.isAdvanced()) {
            return RoundStatus.ADVANCED;
        }
        if (round.isFinalized()) {
            return RoundStatus.FINALIZED;
        }
        return (matches == null || matches.isEmpty()) ? RoundStatus.OPEN : round.computeStatus(matches);
    }
}
