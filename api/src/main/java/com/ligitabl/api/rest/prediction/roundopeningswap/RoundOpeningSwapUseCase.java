package com.ligitabl.api.rest.prediction.roundopeningswap;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.rest.prediction.makeswap.SwapCommand;
import com.ligitabl.api.rest.prediction.makeswap.SwapError;
import com.ligitabl.api.rest.prediction.shared.SwapHelper;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.SeasonPredictionRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RoundOpeningSwapUseCase {

    private static final int MIN_SWAPS = 1;
    private static final int MAX_SWAPS = 2;
    private static final int ROUND_ZERO = 0;

    private final SeasonPredictionRepo predictionRepo;
    private final Clock clock;
    private final SwapHelper swapHelper;

    private record Ctx(Season season, Round round, SeasonPrediction prediction) {}

    public Either<SwapError, RoundOpeningSwapResult> execute(UUID userId, RoundOpeningSwapCommand command) {
        return swapHelper
                .getActiveSeason()
                .flatMap(season -> swapHelper.getCurrentRound(season).map(round -> new Ctx(season, round, null)))
                .flatMap(ctx -> swapHelper.validateRoundOpen(ctx.round()).map(__ -> ctx))
                .flatMap(ctx -> swapHelper
                        .getPrediction(userId, ctx.season().getId())
                        .map(p -> new Ctx(ctx.season(), ctx.round(), p)))
                .flatMap(ctx ->
                        validateOpeningNotUsed(ctx.prediction(), ctx.round()).map(__ -> ctx))
                .flatMap(ctx -> validateBatchSize(command.swaps()).map(__ -> ctx))
                .flatMap(ctx -> applySwaps(ctx.prediction(), command.swaps(), ctx.season(), ctx.round()));
    }

    /**
     * <pre>
     * path                        atRoundNumber  openingCommittedRound  at round N
     * pre-season register         0              0                      merge first
     * merged at N                 N              N                      already reshuffled
     * new join at N               N              N                      already reshuffled
     * joined/merged at N-1        N-1            N-1                    allowed — this is it
     *
     * Join and merge both stamp openingCommittedRound = atRoundNumber: joining consumes the
     * opening window of the round you start playing. Legacy rows predating that column, and
     * rows backfilled to 0, still fall to the atRoundNumber check above.
     * </pre>
     */
    private Either<SwapError, Void> validateOpeningNotUsed(SeasonPrediction prediction, Round round) {
        if (prediction.getAtRoundNumber() == ROUND_ZERO) {
            return Either.left(new SwapError.PreSeasonMergeRequired(round.getPosition()));
        }
        if (prediction.getAtRoundNumber() == round.getPosition()
                || prediction.getOpeningCommittedRound() == round.getPosition()) {
            return Either.left(new SwapError.OpeningAlreadyUsed(round.getPosition()));
        }
        return Either.right(null);
    }

    private Either<SwapError, Void> validateBatchSize(List<SwapCommand> swaps) {
        int size = swaps == null ? 0 : swaps.size();
        return (size < MIN_SWAPS || size > MAX_SWAPS)
                ? Either.left(new SwapError.BatchSizeInvalid(size))
                : Either.right(null);
    }

    private Either<SwapError, RoundOpeningSwapResult> applySwaps(
            SeasonPrediction prediction, List<SwapCommand> swaps, Season season, Round round) {
        List<TeamRank> currentRankings = new ArrayList<>(prediction.getCurrentRankings());
        Instant now = clock.instant();

        for (SwapCommand swap : swaps) {
            var validated = swapHelper.validateTeams(swap, season, prediction, currentRankings);
            if (validated.isLeft()) return Either.left(validated.getLeft());
            var teams = validated.get();
            prediction.addSwap(
                    round.getPosition(), swapHelper.applySwap(currentRankings, teams.teamA(), teams.teamB(), now));
        }

        prediction.setCurrentRankings(currentRankings);
        prediction.setOpeningCommittedRound(round.getPosition());
        prediction.setLastSwapAt(now);
        prediction.setAtRoundNumber(round.getPosition());

        SeasonPrediction saved = predictionRepo.save(prediction);

        return Either.right(new RoundOpeningSwapResult(true, swaps.size(), saved));
    }
}
