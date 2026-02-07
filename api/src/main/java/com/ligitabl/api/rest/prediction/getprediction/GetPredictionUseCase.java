package com.ligitabl.api.rest.prediction.getprediction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.shared.RankingSource;
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
public class GetPredictionUseCase {

    private static final Duration SWAP_COOLDOWN = Duration.ofHours(24);

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
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
                .findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<GetPredictionError, Season>right)
                .orElseGet(() -> Either.left(new GetPredictionError.NotFound()));
    }

    private GetPredictionResult buildResult(Season season, Round currentRound, PredictionSnapshot snapshot) {
        RoundStatus roundStatus = resolveRoundStatus(currentRound);
        GetPredictionResult.SwapStatus swapStatus = resolveSwapStatus(season, snapshot.prediction(), roundStatus);
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

        var matches = matchRepo.findByRoundId(currentRound.getId());
        return (matches == null || matches.isEmpty()) ? RoundStatus.OPEN : currentRound.computeStatus(matches);
    }

    private GetPredictionResult.SwapStatus resolveSwapStatus(
            Season season, SeasonPrediction prediction, RoundStatus roundStatus) {
        if (prediction == null) {
            return new GetPredictionResult.SwapStatus(false, "NO_PREDICTION", null, null);
        }

        if (season.isCompleted()) {
            return new GetPredictionResult.SwapStatus(false, "SEASON_COMPLETED", null, null);
        }

        if (roundStatus != RoundStatus.OPEN) {
            return new GetPredictionResult.SwapStatus(false, "ROUND_NOT_OPEN", null, null);
        }

        Instant lastSwapAt = prediction.getLastSwapAt();
        if (lastSwapAt == null) {
            return new GetPredictionResult.SwapStatus(true, null, null, null);
        }

        Instant now = clock.instant();
        Instant nextSwapAt = lastSwapAt.plus(SWAP_COOLDOWN);

        if (now.isBefore(nextSwapAt)) {
            double hoursRemaining = Duration.between(now, nextSwapAt).toMinutes() / 60.0;
            return new GetPredictionResult.SwapStatus(false, "COOLDOWN_ACTIVE", nextSwapAt, hoursRemaining);
        }

        return new GetPredictionResult.SwapStatus(true, null, null, null);
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
                    null, null, RankingSource.ROUND_STANDINGS, toTeamRanks(standings.get()), List.of(), null, null));
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
