package com.ligitabl.api.web.publicpredictions;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.shared.dto.PublicRankDto;
import com.ligitabl.api.web.shared.season.SeasonPredictionSupport;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSwap;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.StandingsRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.AllArgsConstructor;

/**
 * Use case for the public, no-login prediction view — always read-only, so it has no access-mode
 * concept. Three shapes: target user not found (or has no prediction) → current standings only;
 * a historical/scored round → the user's {@link RoundResult}; the current round → the user's
 * current (or, pre-season, initial) rankings against current standings.
 */
@Service
@AllArgsConstructor
public class GetPublicPredictionUseCase {
    private final SeasonPredictionSupport seasonPredictionSupport;
    private final RoundResultRepo roundResultRepo;
    private final StandingsRepo standingsRepo;
    private final MatchRepo matchRepo;
    private final UserRepo userRepo;
    private final TeamRepo teamRepo;
    private final EntryRepo entryRepo;

    public sealed interface Error {
        record SeasonNotFound(UUID seasonId) implements Error {}

        record CurrentRoundNotFound(UUID seasonId) implements Error {}
    }

    /** Resolved request-scoped state, accumulated step by step so {@link #execute} stays flat. */
    private record Ctx(
            Season season,
            int currentRound,
            int lastRound,
            boolean seasonCompleted,
            Optional<User> userOpt,
            Optional<SeasonPrediction> predictionOpt,
            int minRound,
            int viewingRound,
            String targetDisplayName) {
        Ctx(Season season, Round currentRoundEntity) {
            this(
                    season,
                    currentRoundEntity.getPosition(),
                    season.getMaxRounds(),
                    currentRoundEntity.isAdvanced() && currentRoundEntity.getPosition() == season.getMaxRounds(),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0,
                    null);
        }

        boolean showHistorical() {
            return viewingRound < currentRound || (viewingRound == currentRound && seasonCompleted);
        }
    }

    public Either<Error, PublicPredictionViewData> execute(GetPublicPredictionQuery query) {
        return resolveSeason(query.seasonId())
                .flatMap(this::resolveCurrentRound)
                .map(ctx -> withUserAndRound(ctx, query))
                .map(this::buildViewData);
    }

    private Either<Error, Season> resolveSeason(UUID seasonId) {
        return Either.ofOptional(
                seasonPredictionSupport.findSeasonById(seasonId), () -> new Error.SeasonNotFound(seasonId));
    }

    private Either<Error, Ctx> resolveCurrentRound(Season season) {
        return Either.<Error, Round>ofOptional(
                        seasonPredictionSupport.findCurrentRound(season),
                        () -> new Error.CurrentRoundNotFound(season.getId()))
                .map(currentRoundEntity -> new Ctx(season, currentRoundEntity));
    }

    private Ctx withUserAndRound(Ctx ctx, GetPublicPredictionQuery query) {
        Optional<User> userOpt = resolveUser(query.publicId());
        Optional<SeasonPrediction> predictionOpt = userOpt.flatMap(user -> seasonPredictionSupport.findPrediction(
                user.getId(), ctx.season().getId()));
        int minRound = predictionOpt.isEmpty() ? ctx.currentRound() : minRoundForNav(userOpt, ctx);
        int viewingRound = clampRound(query.requestedRound(), minRound, ctx.currentRound());
        String targetDisplayName = userOpt.map(user -> user.getDisplayName()).orElse(null);

        return new Ctx(
                ctx.season(),
                ctx.currentRound(),
                ctx.lastRound(),
                ctx.seasonCompleted(),
                userOpt,
                predictionOpt,
                minRound,
                viewingRound,
                targetDisplayName);
    }

    /**
     * How far back the round navigation may go for the target user.
     */
    private int minRoundForNav(Optional<User> userOpt, Ctx ctx) {
        UUID mainContestId = ctx.season().getMainContestId();
        if (mainContestId == null) {
            return ctx.currentRound();
        }
        return userOpt.flatMap(user -> entryRepo.findByUserAndContest(user.getId(), mainContestId))
                .map(entry -> Math.max(1, entry.getJoinedAtRound()))
                .orElse(ctx.currentRound());
    }

    private PublicPredictionViewData buildViewData(Ctx ctx) {
        if (ctx.predictionOpt().isEmpty()) {
            return buildFallbackView(ctx, ctx.userOpt().isPresent());
        }

        return ctx.showHistorical()
                ? buildHistoricalView(ctx)
                : buildCurrentView(ctx, ctx.predictionOpt().get());
    }

    private int clampRound(Integer requested, int minRound, int currentRound) {
        if (requested == null) return currentRound;
        if (requested < minRound) return minRound;
        if (requested > currentRound) return currentRound;
        return requested;
    }

    private Optional<User> resolveUser(String publicId) {
        try {
            return userRepo.findByPublicId(PublicId.create(publicId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Target user not found, or found but never made a prediction this season — current
     * standings only, predicted == actual (delta always 0) since there's nothing to compare.
     */
    private PublicPredictionViewData buildFallbackView(Ctx ctx, boolean userFound) {
        Season season = ctx.season();
        StandingsData standingsData = fetchStandingsData(season.getId(), ctx.currentRound())
                .orElseGet(() -> StandingsData.fromBaseline(season.getInitialRankings()));
        List<PublicRankDto> rows = PublicRankDto.listOfPrediction(
                standingsData.rankings(), standingsData.positions(), teamsByCode(standingsData.rankings()));

        return PublicPredictionViewData.builder()
                .rows(rows)
                .userFound(userFound)
                .hasPrediction(false)
                .targetDisplayName(ctx.targetDisplayName())
                .currentRound(ctx.currentRound())
                .lastRound(ctx.lastRound())
                .viewingRound(ctx.currentRound())
                .minRound(ctx.minRound())
                .seasonCompleted(ctx.seasonCompleted())
                .hasRoundResult(false)
                .matches(matchRepo.findBySeasonAndRound(season.getId(), ctx.currentRound()))
                .standingsMap(standingsData.positions())
                .pointsMap(standingsData.points())
                .goalDifferenceMap(standingsData.goalDifference())
                .build();
    }

    private PublicPredictionViewData buildHistoricalView(Ctx ctx) {
        User user = ctx.userOpt().get();
        Optional<RoundResult> roundResultOpt = roundResultRepo.findByUserAndRound(user.getId(), ctx.viewingRound());

        if (roundResultOpt.isEmpty()) {
            // Shouldn't normally happen once round clamping is correct, but degrade gracefully
            // rather than error on a public-facing page.
            return buildFallbackView(ctx, true);
        }

        RoundResult roundResult = roundResultOpt.get();
        var sortedRanks = roundResult.getRankings().stream()
                .sorted(Comparator.comparingInt(r -> r.getRanking().getPosition()))
                .toList();
        Map<String, Team> teamsByCode = teamsByCodeFromCodes(
                sortedRanks.stream().map(r -> r.getRanking().getCode()).collect(Collectors.toSet()));

        return PublicPredictionViewData.builder()
                .rows(PublicRankDto.listOfResults(sortedRanks, teamsByCode))
                .userFound(true)
                .hasPrediction(true)
                .targetDisplayName(ctx.targetDisplayName())
                .currentRound(ctx.currentRound())
                .lastRound(ctx.lastRound())
                .viewingRound(ctx.viewingRound())
                .minRound(ctx.minRound())
                .seasonCompleted(ctx.seasonCompleted())
                .hasRoundResult(true)
                .totalScore(roundResult.getTotalScore())
                .totalHits(sortedRanks.stream().mapToInt(r -> r.getHit()).sum())
                .zeroesCount(
                        (int) sortedRanks.stream().filter(r -> r.getHit() == 0).count())
                .roundSwaps(swapsForRound(ctx.predictionOpt(), ctx.viewingRound()))
                .build();
    }

    private PublicPredictionViewData buildCurrentView(Ctx ctx, SeasonPrediction prediction) {
        Season season = ctx.season();
        List<TeamRank> predictedRanks;
        StandingsData standingsData;

        if (prediction.isPreSeasonRegistration()) {
            predictedRanks = prediction.getInitialRankings();
            standingsData = StandingsData.fromBaseline(season.getInitialRankings());
        } else {
            predictedRanks = prediction.getCurrentRankings();
            standingsData = fetchStandingsData(season.getId(), ctx.currentRound())
                    .orElseGet(() -> StandingsData.fromBaseline(season.getInitialRankings()));
        }

        var sortedRanks = TeamRank.inPositionOrder(predictedRanks);
        Map<String, Team> teamsByCode =
                teamsByCodeFromCodes(sortedRanks.stream().map(r -> r.getCode()).collect(Collectors.toSet()));

        return PublicPredictionViewData.builder()
                .rows(PublicRankDto.listOfPrediction(sortedRanks, standingsData.positions(), teamsByCode))
                .userFound(true)
                .hasPrediction(true)
                .targetDisplayName(ctx.targetDisplayName())
                .currentRound(ctx.currentRound())
                .lastRound(ctx.lastRound())
                .viewingRound(ctx.currentRound())
                .minRound(ctx.minRound())
                .seasonCompleted(ctx.seasonCompleted())
                .hasRoundResult(false)
                .matches(matchRepo.findBySeasonAndRound(season.getId(), ctx.currentRound()))
                .standingsMap(standingsData.positions())
                .pointsMap(standingsData.points())
                .goalDifferenceMap(standingsData.goalDifference())
                .roundSwaps(swapsForRound(Optional.of(prediction), ctx.currentRound()))
                .build();
    }

    /**
     * The target user's swaps for one round — same lookup {@code GetUserPredictionUseCase} does for
     * the owner's own view, so the public page's "Swap history" lists exactly what the owner sees.
     */
    private List<SwapChange> swapsForRound(Optional<SeasonPrediction> predictionOpt, int round) {
        return predictionOpt.map(SeasonPrediction::getSwaps).orElseGet(List::of).stream()
                .filter(rs -> rs.getRound() == round)
                .findFirst()
                .map(RoundSwap::getChanges)
                .orElseGet(List::of);
    }

    /** Positions, points, and GD from a single {@code Standings} row, plus its raw rankings. */
    private record StandingsData(
            List<TeamRank> rankings,
            Map<String, Integer> positions,
            Map<String, Integer> points,
            Map<String, Integer> goalDifference) {
        static StandingsData fromBaseline(List<TeamRank> baseline) {
            Map<String, Integer> positions = new HashMap<>();
            for (TeamRank rank : baseline) {
                positions.put(rank.getCode(), rank.getPosition());
            }
            return new StandingsData(baseline, positions, Map.of(), Map.of());
        }
    }

    private Optional<StandingsData> fetchStandingsData(UUID seasonId, int roundPosition) {
        return standingsRepo
                .findBySeasonAndRoundPosition(seasonId, roundPosition)
                .map(standings -> {
                    List<TeamRank> rankings = standings.getRankings().stream()
                            .map(str -> str.getRanking())
                            .toList();
                    Map<String, Integer> positions = new HashMap<>();
                    Map<String, Integer> points = new HashMap<>();
                    Map<String, Integer> goalDifference = new HashMap<>();
                    for (var rank : standings.getRankings()) {
                        String code = rank.getRanking().getCode();
                        positions.put(code, rank.getRanking().getPosition());
                        if (rank.getMetadata() != null) {
                            points.put(code, rank.getMetadata().getPoints());
                            goalDifference.put(code, rank.getMetadata().getGd());
                        }
                    }
                    return new StandingsData(rankings, positions, points, goalDifference);
                });
    }

    private Map<String, Team> teamsByCode(List<TeamRank> ranks) {
        return teamsByCodeFromCodes(ranks.stream().map(r -> r.getCode()).collect(Collectors.toSet()));
    }

    private Map<String, Team> teamsByCodeFromCodes(Set<String> codes) {
        return teamRepo.findAllByCodes(codes).stream().collect(Collectors.toMap(t -> t.getCode(), t -> t));
    }
}
