package com.ligitabl.api.usecases.seasonprediction.getseasonpred;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GetSeasonPredictionUseCase {

    private static final Duration SWAP_COOLDOWN = Duration.ofHours(24);

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final StandingsRepo standingsRepo;
    private final SeasonPredictionRankEnricher rankEnricher;
    private final Clock clock;

    public Either<GetSeasonPredictionError, GetSeasonPredictionResult> execute(UUID userId) {
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

    private Either<GetSeasonPredictionError, Season> getCurrentSeason() {
        return seasonRepo
                .findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<GetSeasonPredictionError, Season>right)
                .orElseGet(() -> Either.left(new GetSeasonPredictionError.SeasonNotFound()));
    }

    private GetSeasonPredictionResult buildResult(Season season, Round currentRound, PredictionSnapshot snapshot) {
        RoundStatus roundStatus = resolveRoundStatus(currentRound);
        GetSeasonPredictionResult.SwapStatus swapStatus = resolveSwapStatus(season, snapshot.prediction(), roundStatus);
        List<SeasonPredictionRankDto> enrichedRankings = rankEnricher.enrich(snapshot.rankings());

        return new GetSeasonPredictionResult(
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

    private GetSeasonPredictionResult buildResultWithoutRound(Season season, PredictionSnapshot snapshot) {
        GetSeasonPredictionResult.SwapStatus swapStatus =
                resolveSwapStatus(season, snapshot.prediction(), RoundStatus.OPEN);
        List<SeasonPredictionRankDto> enrichedRankings = rankEnricher.enrich(snapshot.rankings());

        return new GetSeasonPredictionResult(
                snapshot.predictionId(),
                season.getId(),
                snapshot.atRoundNumber(),
                0,
                "UNKNOWN",
                season.isCompleted(),
                snapshot.source(),
                enrichedRankings,
                snapshot.swaps(),
                snapshot.lastSwapAt(),
                swapStatus);
    }

    private Either<GetSeasonPredictionError, Round> getCurrentRound(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            return Either.left(new GetSeasonPredictionError.SeasonHasNoCurrentRound(season.getId()));
        }

        return roundRepo
                .findById(currentRoundId)
                .map(Either::<GetSeasonPredictionError, Round>right)
                .orElseGet(() -> Either.left(new GetSeasonPredictionError.CurrentRoundNotFound(currentRoundId)));
    }

    private RoundStatus resolveRoundStatus(Round currentRound) {
        if (currentRound.isFinalized()) {
            return RoundStatus.FINALISED;
        }

        var matches = matchRepo.findByRoundId(currentRound.getId());
        return (matches == null || matches.isEmpty()) ? RoundStatus.OPEN : currentRound.computeStatus(matches);
    }

    private GetSeasonPredictionResult.SwapStatus resolveSwapStatus(
            Season season, SeasonPrediction prediction, RoundStatus roundStatus) {
        if (prediction == null) {
            return new GetSeasonPredictionResult.SwapStatus(false, "NO_PREDICTION", null, null);
        }

        if (season.isCompleted()) {
            return new GetSeasonPredictionResult.SwapStatus(false, "SEASON_COMPLETED", null, null);
        }

        if (roundStatus != RoundStatus.OPEN) {
            return new GetSeasonPredictionResult.SwapStatus(false, "ROUND_NOT_OPEN", null, null);
        }

        Instant lastSwapAt = prediction.getLastSwapAt();
        if (lastSwapAt == null) {
            return new GetSeasonPredictionResult.SwapStatus(true, null, null, null);
        }

        Instant now = clock.instant();
        Instant nextSwapAt = lastSwapAt.plus(SWAP_COOLDOWN);

        if (now.isBefore(nextSwapAt)) {
            double hoursRemaining = Duration.between(now, nextSwapAt).toMinutes() / 60.0;
            return new GetSeasonPredictionResult.SwapStatus(false, "COOLDOWN_ACTIVE", nextSwapAt, hoursRemaining);
        }

        return new GetSeasonPredictionResult.SwapStatus(true, null, null, null);
    }

    private Either<GetSeasonPredictionError, PredictionSnapshot> resolvePredictionSnapshot(
            Season season, UUID userId, int currentRoundNumber) {
        return predictionRepo
                .findByUserAndSeason(userId, season.getId())
                .map(prediction -> Either.<GetSeasonPredictionError, PredictionSnapshot>right(new PredictionSnapshot(
                        prediction.getId(),
                        prediction.getAtRoundNumber(),
                        RankingSource.USER_PREDICTION,
                        prediction.getCurrentRankings(),
                        prediction.getSwaps(),
                        prediction.getLastSwapAt(),
                        prediction)))
                .orElseGet(() -> resolveFallbackSnapshot(season, currentRoundNumber));
    }

    private Either<GetSeasonPredictionError, PredictionSnapshot> resolveFallbackSnapshot(
            Season season, int currentRoundNumber) {
        var standings = standingsRepo.findBySeasonAndRoundPosition(season.getId(), currentRoundNumber);
        if (standings.isPresent()
                && standings.get().getRankings() != null
                && !standings.get().getRankings().isEmpty()) {
            return Either.right(new PredictionSnapshot(
                    null, null, RankingSource.ROUND_STANDINGS, toTeamRanks(standings.get()), List.of(), null, null));
        }

        List<TeamRank> baseline = season.getInitialRankings();
        if (baseline == null || baseline.isEmpty()) {
            return Either.left(new GetSeasonPredictionError.BaselineRankingsMissing(season.getId()));
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
