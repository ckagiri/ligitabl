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
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
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
    private final UserRepo userRepo;
    private final TeamRepo teamRepo;

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
        return Either.ofOptional(seasonPredictionSupport.findSeasonById(seasonId), () -> new Error.SeasonNotFound(seasonId));
    }

    private Either<Error, Ctx> resolveCurrentRound(Season season) {
        return Either.<Error, Round>ofOptional(
                        seasonPredictionSupport.findCurrentRound(season), () -> new Error.CurrentRoundNotFound(season.getId()))
                .map(currentRoundEntity -> new Ctx(season, currentRoundEntity));
    }

    private Ctx withUserAndRound(Ctx ctx, GetPublicPredictionQuery query) {
        Optional<User> userOpt = resolveUser(query.publicId());
        Optional<SeasonPrediction> predictionOpt = userOpt.flatMap(
                user -> seasonPredictionSupport.findPrediction(user.getId(), ctx.season().getId()));
        int minRound = predictionOpt.map(sp -> Math.max(1, sp.getAtRoundNumber())).orElse(ctx.currentRound());
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

    private PublicPredictionViewData buildViewData(Ctx ctx) {
        if (ctx.predictionOpt().isEmpty()) {
            return buildFallbackView(ctx, ctx.userOpt().isPresent());
        }

        return ctx.showHistorical() ? buildHistoricalView(ctx) : buildCurrentView(ctx, ctx.predictionOpt().get());
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
        List<TeamRank> standingsRanks = standingsRankings(ctx.season().getId(), ctx.currentRound())
                .orElseGet(() -> ctx.season().getInitialRankings());
        Map<String, Integer> positions = positionsByCode(standingsRanks);
        List<PublicRankDto> rows =
                PublicRankDto.listOfPrediction(standingsRanks, positions, teamsByCode(standingsRanks));

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
                .zeroesCount((int) sortedRanks.stream().filter(r -> r.getHit() == 0).count())
                .build();
    }

    private PublicPredictionViewData buildCurrentView(Ctx ctx, SeasonPrediction prediction) {
        Season season = ctx.season();
        List<TeamRank> predictedRanks;
        Map<String, Integer> actualPositions;

        if (prediction.isPreSeasonRegistration()) {
            predictedRanks = prediction.getInitialRankings();
            actualPositions = positionsByCode(season.getInitialRankings());
        } else {
            predictedRanks = prediction.getCurrentRankings();
            actualPositions = standingsRankings(season.getId(), ctx.currentRound())
                    .map(ranks -> positionsByCode(ranks))
                    .orElseGet(() -> positionsByCode(season.getInitialRankings()));
        }

        var sortedRanks =
                predictedRanks.stream().sorted(Comparator.comparingInt(r -> r.getPosition())).toList();
        Map<String, Team> teamsByCode =
                teamsByCodeFromCodes(sortedRanks.stream().map(r -> r.getCode()).collect(Collectors.toSet()));

        return PublicPredictionViewData.builder()
                .rows(PublicRankDto.listOfPrediction(sortedRanks, actualPositions, teamsByCode))
                .userFound(true)
                .hasPrediction(true)
                .targetDisplayName(ctx.targetDisplayName())
                .currentRound(ctx.currentRound())
                .lastRound(ctx.lastRound())
                .viewingRound(ctx.currentRound())
                .minRound(ctx.minRound())
                .seasonCompleted(ctx.seasonCompleted())
                .hasRoundResult(false)
                .build();
    }

    private Optional<List<TeamRank>> standingsRankings(UUID seasonId, int roundPosition) {
        return standingsRepo
                .findBySeasonAndRoundPosition(seasonId, roundPosition)
                .map(standings -> standings.getRankings())
                .map(rankings -> rankings.stream().map(str -> str.getRanking()).toList());
    }

    private Map<String, Integer> positionsByCode(List<TeamRank> ranks) {
        Map<String, Integer> positions = new HashMap<>();
        for (TeamRank rank : ranks) {
            positions.put(rank.getCode(), rank.getPosition());
        }
        return positions;
    }

    private Map<String, Team> teamsByCode(List<TeamRank> ranks) {
        return teamsByCodeFromCodes(ranks.stream().map(r -> r.getCode()).collect(Collectors.toSet()));
    }

    private Map<String, Team> teamsByCodeFromCodes(Set<String> codes) {
        return teamRepo.findAllByCodes(codes).stream().collect(Collectors.toMap(t -> t.getCode(), t -> t));
    }
}
