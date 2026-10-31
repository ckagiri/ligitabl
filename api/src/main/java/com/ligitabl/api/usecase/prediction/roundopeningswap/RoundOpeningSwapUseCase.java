package com.ligitabl.api.usecase.prediction.roundopeningswap;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.prediction.makeswap.SwapCommand;
import com.ligitabl.api.usecase.prediction.makeswap.SwapError;
import com.ligitabl.api.usecase.prediction.shared.SwapHelper;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.RoundStatus;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.prediction.SeasonPrediction;
import com.ligitabl.model.domain.prediction.TeamSwap;
import com.ligitabl.model.repo.SeasonPredictionRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RoundOpeningSwapUseCase {

    private static final int MIN_SWAPS = 1;
    private static final int MAX_SWAPS = 2;

    private final SeasonPredictionRepo predictionRepo;
    private final Clock clock;
    private final SwapHelper swapHelper;

    private record Ctx(Season season, Round round, RoundStatus status, SeasonPrediction prediction) {}

    /**
     * <pre>
     * path                        atRoundNumber  openingCommittedRound  at round N
     * pre-season register         0              0                      merge first
     * merged at N                 N              N                      already reshuffled
     * new join at N               N              N                      already reshuffled
     * joined/merged at N-1        N-1            N-1                    allowed — this is it
     * free swap at N after N-1    N              N-1                    allowed
     * </pre>
     *
     * See {@link SeasonPrediction#openingSwapEligibility}.
     */
    public Either<SwapError, RoundOpeningSwapResult> execute(UUID userId, RoundOpeningSwapCommand command) {
        Instant now = clock.instant();
        return swapHelper
                .getActiveSeason()
                .flatMap(season -> swapHelper
                        .getCurrentRound(season)
                        .map(round -> new Ctx(season, round, swapHelper.roundStatus(round), null)))
                .flatMap(ctx -> swapHelper
                        .getPrediction(userId, ctx.season().getId())
                        .map(p -> new Ctx(ctx.season(), ctx.round(), ctx.status(), p)))
                .flatMap(ctx -> swapHelper
                        .validateEligibility(
                                ctx.prediction()
                                        .openingSwapEligibility(ctx.round().getPosition(), ctx.status()),
                                ctx.round().getPosition(),
                                now)
                        .map(__ -> ctx))
                .flatMap(ctx -> validateBatchSize(command.swaps()).map(__ -> ctx))
                .flatMap(ctx -> validateTeams(command.swaps(), ctx).map(batch -> applySwaps(ctx, batch, now)));
    }

    private Either<SwapError, Void> validateBatchSize(List<SwapCommand> swaps) {
        int size = swaps == null ? 0 : swaps.size();
        return (size < MIN_SWAPS || size > MAX_SWAPS)
                ? Either.left(new SwapError.BatchSizeInvalid(size))
                : Either.right(null);
    }

    private Either<SwapError, List<TeamSwap>> validateTeams(List<SwapCommand> swaps, Ctx ctx) {
        List<TeamSwap> batch = new ArrayList<>();
        for (SwapCommand swap : swaps) {
            var validated = swapHelper.validateTeams(swap, ctx.season(), ctx.prediction());
            if (validated.isLeft()) return Either.left(validated.getLeft());
            batch.add(validated.get());
        }
        return Either.right(batch);
    }

    private RoundOpeningSwapResult applySwaps(Ctx ctx, List<TeamSwap> batch, Instant now) {
        SeasonPrediction prediction = ctx.prediction();
        prediction.applyOpeningSwaps(batch, ctx.round().getPosition(), ctx.status(), now);
        SeasonPrediction saved = predictionRepo.save(prediction);
        return new RoundOpeningSwapResult(true, batch.size(), saved);
    }
}
