package com.ligitabl.api.rest.prediction.roundopeningswap;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.makeswap.SwapCommand;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RoundOpeningSwapUseCase {

    private static final int MIN_SWAPS = 1;
    private static final int MAX_SWAPS = 5;

    private final CompetitionDefaults competitionDefaults;
    private final SeasonPredictionRepo predictionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final Clock clock;

    private record Ctx(Season season, Round round, SeasonPrediction prediction) {}

    public Either<RoundOpeningSwapError, RoundOpeningSwapResult> execute(UUID userId, RoundOpeningSwapCommand command) {
        return getCurrentSeason()
                .flatMap(season -> validateSeasonNotCompleted(season).map(__ -> season))
                .flatMap(season -> getCurrentRound(season).map(round -> new Ctx(season, round, null)))
                .flatMap(ctx -> validateRoundOpen(ctx.round()).map(__ -> ctx))
                .flatMap(ctx ->
                        getPrediction(userId, ctx.season().getId()).map(p -> new Ctx(ctx.season(), ctx.round(), p)))
                .flatMap(ctx ->
                        validateOpeningNotUsed(ctx.prediction(), ctx.round()).map(__ -> ctx))
                .flatMap(ctx -> validateBatchSize(command.swaps()).map(__ -> ctx))
                .flatMap(ctx -> applySwaps(ctx.prediction(), command.swaps(), ctx.season(), ctx.round()));
    }

    private Either<RoundOpeningSwapError, Season> getCurrentSeason() {
        return seasonRepo
                .findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<RoundOpeningSwapError, Season>right)
                .orElseGet(() -> Either.left(new RoundOpeningSwapError.NoPredictionFound(null, null)));
    }

    private Either<RoundOpeningSwapError, Void> validateSeasonNotCompleted(Season season) {
        return season.isCompleted() ? Either.left(new RoundOpeningSwapError.SeasonCompleted()) : Either.right(null);
    }

    private Either<RoundOpeningSwapError, Round> getCurrentRound(Season season) {
        return roundRepo
                .findById(season.getCurrentRoundId())
                .map(Either::<RoundOpeningSwapError, Round>right)
                .orElseGet(() -> Either.left(new RoundOpeningSwapError.CurrentRoundNotFound(season.getId())));
    }

    private Either<RoundOpeningSwapError, Void> validateRoundOpen(Round round) {
        var matches = matchRepo.findByRoundId(round.getId());
        RoundStatus status = (matches == null || matches.isEmpty()) ? RoundStatus.OPEN : round.computeStatus(matches);
        return status == RoundStatus.OPEN
                ? Either.right(null)
                : Either.left(new RoundOpeningSwapError.RoundNotOpen(status.name()));
    }

    private Either<RoundOpeningSwapError, SeasonPrediction> getPrediction(UUID userId, UUID seasonId) {
        return predictionRepo
                .findByUserAndSeason(userId, seasonId)
                .map(Either::<RoundOpeningSwapError, SeasonPrediction>right)
                .orElseGet(() -> Either.left(new RoundOpeningSwapError.NoPredictionFound(userId, seasonId)));
    }

    private Either<RoundOpeningSwapError, Void> validateOpeningNotUsed(SeasonPrediction prediction, Round round) {
        return prediction.getOpeningCommittedRound() == round.getPosition()
                ? Either.left(new RoundOpeningSwapError.OpeningAlreadyUsed(round.getPosition()))
                : Either.right(null);
    }

    private Either<RoundOpeningSwapError, Void> validateBatchSize(List<SwapCommand> swaps) {
        int size = swaps == null ? 0 : swaps.size();
        return (size < MIN_SWAPS || size > MAX_SWAPS)
                ? Either.left(new RoundOpeningSwapError.BatchSizeInvalid(size))
                : Either.right(null);
    }

    private Either<RoundOpeningSwapError, RoundOpeningSwapResult> applySwaps(
            SeasonPrediction prediction, List<SwapCommand> swaps, Season season, Round round) {
        List<TeamRank> currentRankings = new ArrayList<>(prediction.getCurrentRankings());

        List<TeamRank> initialRankings =
                season.getInitialRankings() != null ? season.getInitialRankings() : prediction.getInitialRankings();
        Set<String> validCodes = initialRankings == null
                ? Set.of()
                : initialRankings.stream().map(TeamRank::getCode).collect(Collectors.toSet());

        Instant now = clock.instant();

        for (SwapCommand swap : swaps) {
            String teamACode = swap.teamACode().toUpperCase();
            String teamBCode = swap.teamBCode().toUpperCase();

            if (!validCodes.contains(teamACode)) {
                return Either.left(new RoundOpeningSwapError.InvalidTeamCode(teamACode));
            }
            if (!validCodes.contains(teamBCode)) {
                return Either.left(new RoundOpeningSwapError.InvalidTeamCode(teamBCode));
            }

            TeamRank teamA = currentRankings.stream()
                    .filter(t -> t.getCode().equals(teamACode))
                    .findFirst()
                    .orElse(null);
            TeamRank teamB = currentRankings.stream()
                    .filter(t -> t.getCode().equals(teamBCode))
                    .findFirst()
                    .orElse(null);

            if (teamA == null || teamB == null) {
                return Either.left(new RoundOpeningSwapError.TeamsNotFound(teamACode, teamBCode));
            }

            int indexA = currentRankings.indexOf(teamA);
            int indexB = currentRankings.indexOf(teamB);

            TeamRank newTeamA = teamA.withPosition(teamB.getPosition());
            TeamRank newTeamB = teamB.withPosition(teamA.getPosition());

            currentRankings.set(indexA, newTeamA);
            currentRankings.set(indexB, newTeamB);

            SwapChange change = new SwapChange(
                    now,
                    String.format("%s:%d→%d", teamA.getCode(), teamA.getPosition(), newTeamA.getPosition()),
                    String.format("%s:%d→%d", teamB.getCode(), teamB.getPosition(), newTeamB.getPosition()));

            prediction.addSwap(round.getPosition(), change);
        }

        prediction.setCurrentRankings(currentRankings);
        prediction.setOpeningCommittedRound(round.getPosition());
        prediction.setLastSwapAt(now);

        SeasonPrediction saved = predictionRepo.save(prediction);

        return Either.right(new RoundOpeningSwapResult(true, swaps.size(), saved));
    }
}
