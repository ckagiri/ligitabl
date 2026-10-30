package com.ligitabl.api.usecase.prediction.getprediction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.prediction.getprediction.GetPredictionResult.SwapStatus;
import com.ligitabl.api.usecase.prediction.shared.RankingSource;
import com.ligitabl.api.usecase.round.shared.RoundSupport;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.SwapEligibility;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GetPredictionUseCase {

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final RoundSupport roundSupport;
    private final SeasonPredictionRepo predictionRepo;
    private final StandingsRepo standingsRepo;
    private final PredictionRankEnricher rankEnricher;
    private final Clock clock;

    public Either<GetPredictionError, GetPredictionResult> execute(UUID userId) {
        return getCurrentSeason().flatMap(season -> {
            if (userId != null) {
                return getCurrentRound(season)
                        .flatMap(round -> resolvePredictionSnapshot(season, userId, round.getPosition())
                                .map(snapshot -> buildResult(season, round, snapshot)));
            }

            return getCurrentRound(season).flatMap(round -> resolveFallbackSnapshot(season, round.getPosition())
                    .map(snapshot -> buildResult(season, round, snapshot)));
        });
    }

    private Either<GetPredictionError, Season> getCurrentSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<GetPredictionError, Season>right)
                .orElseGet(() -> Either.left(new GetPredictionError.NotFound()));
    }

    private GetPredictionResult buildResult(Season season, Round currentRound, PredictionSnapshot snapshot) {
        RoundStatus roundStatus = resolveRoundStatus(currentRound);
        GetPredictionResult.SwapStatus swapStatus =
                resolveSwapStatus(season, snapshot.prediction(), currentRound, roundStatus);
        List<PredictionRankDto> enrichedRankings = rankEnricher.enrich(snapshot.rankings());

        return new GetPredictionResult(
                snapshot.predictionId(),
                season.getId(),
                snapshot.atRoundNumber(),
                currentRound.getPosition(),
                roundStatus.name(),
                season.isCompleted(),
                snapshot.source(),
                enrichedRankings,
                snapshot.swaps(),
                snapshot.lastSwapAt(),
                swapStatus);
    }

    private Either<GetPredictionError, Round> getCurrentRound(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            return Either.left(new GetPredictionError.HasNoCurrentRound(season.getId()));
        }

        return roundRepo
                .findById(currentRoundId)
                .map(Either::<GetPredictionError, Round>right)
                .orElseGet(() -> Either.left(new GetPredictionError.CurrentRoundNotFound(currentRoundId)));
    }

    private RoundStatus resolveRoundStatus(Round currentRound) {
        if (currentRound.isFinalized()) {
            return RoundStatus.COMPLETED;
        }

        return roundSupport.resolveStatus(currentRound);
    }

    /** Answers for {@code /swap}, the only swap REST exposes, so it follows MakeSwapUseCase's target round. */
    private SwapStatus resolveSwapStatus(
            Season season, SeasonPrediction prediction, Round currentRound, RoundStatus currentRoundStatus) {
        if (prediction == null) {
            return SwapStatus.blocked("NO_PREDICTION");
        }

        if (season.isCompleted()) {
            return SwapStatus.blocked("SEASON_COMPLETED");
        }

        int target = prediction.swapTargetRound(currentRound.getPosition());
        RoundStatus targetStatus = target == currentRound.getPosition()
                ? currentRoundStatus
                : roundRepo
                        .findBySeasonIdAndPosition(season.getId(), target)
                        .map(this::resolveRoundStatus)
                        .orElse(RoundStatus.UNKNOWN);

        Instant now = clock.instant();
        return switch (prediction.swapEligibility(target, targetStatus, now)) {
            case SwapEligibility.Allowed allowed -> SwapStatus.allowed();
            case SwapEligibility.CooldownActive cooldown -> SwapStatus.cooldown(
                    cooldown.nextSwapAt(),
                    Duration.between(now, cooldown.nextSwapAt()).toMinutes() / 60.0);
            case SwapEligibility.RoundNotOpen r -> SwapStatus.blocked("ROUND_NOT_OPEN");
            case SwapEligibility.PreSeasonMergeRequired p -> SwapStatus.blocked("PRE_SEASON_MERGE_REQUIRED");
            case SwapEligibility.UseOpeningWindowFirst u -> SwapStatus.blocked("USE_OPENING_WINDOW_FIRST");
            case SwapEligibility.OpeningAlreadyUsed o -> SwapStatus.blocked("OPENING_ALREADY_USED");
        };
    }

    private Either<GetPredictionError, PredictionSnapshot> resolvePredictionSnapshot(
            Season season, UUID userId, int currentRoundNumber) {
        return predictionRepo
                .findByUserAndSeason(userId, season.getId())
                .map(prediction -> Either.<GetPredictionError, PredictionSnapshot>right(new PredictionSnapshot(
                        prediction.getId(),
                        prediction.getAtRoundNumber(),
                        RankingSource.USER_PREDICTION,
                        prediction.getCurrentRankings(),
                        prediction.getSwaps(),
                        prediction.getLastSwapAt(),
                        prediction)))
                .orElseGet(() -> resolveFallbackSnapshot(season, currentRoundNumber));
    }

    private Either<GetPredictionError, PredictionSnapshot> resolveFallbackSnapshot(
            Season season, int currentRoundNumber) {
        var standings = standingsRepo.findBySeasonAndRoundPosition(season.getId(), currentRoundNumber);
        if (standings.isPresent()
                && standings.get().getRankings() != null
                && !standings.get().getRankings().isEmpty()) {
            return Either.right(new PredictionSnapshot(
                    null,
                    null,
                    RankingSource.CURRENT_ROUND_STANDINGS,
                    toTeamRanks(standings.get()),
                    List.of(),
                    null,
                    null));
        }

        List<TeamRank> baseline = season.getInitialRankings();
        if (baseline == null || baseline.isEmpty()) {
            return Either.left(new GetPredictionError.BaselineRankingsMissing(season.getId()));
        }

        return Either.right(
                new PredictionSnapshot(null, null, RankingSource.SEASON_BASELINE, baseline, List.of(), null, null));
    }

    private List<TeamRank> toTeamRanks(Standings standings) {
        return standings.getRankings().stream()
                .map(StandingsTeamRank::getRanking)
                .toList();
    }

    private record PredictionSnapshot(
            UUID predictionId,
            Integer atRoundNumber,
            RankingSource source,
            List<TeamRank> rankings,
            List<com.ligitabl.model.domain.RoundSwap> swaps,
            Instant lastSwapAt,
            SeasonPrediction prediction) {}
}
