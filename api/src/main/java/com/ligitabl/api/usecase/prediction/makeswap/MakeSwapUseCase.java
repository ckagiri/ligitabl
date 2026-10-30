package com.ligitabl.api.usecase.prediction.makeswap;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.prediction.shared.SwapHelper;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MakeSwapUseCase {

    private final RoundRepo roundRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final Clock clock;
    private final SwapHelper swapHelper;

    private record Ctx(Season season, Round targetRound, SeasonPrediction prediction, RoundStatus status) {
        Ctx(Season season, Round targetRound, SeasonPrediction prediction) {
            this(season, targetRound, prediction, null);
        }
    }

    public Either<SwapError, SwapResult> execute(UUID userId, SwapCommand command) {
        Instant now = clock.instant();
        return swapHelper
                .getActiveSeason()
                .flatMap(season ->
                        swapHelper.getPrediction(userId, season.getId()).map(p -> new Ctx(season, null, p)))
                .flatMap(ctx -> swapHelper
                        .getCurrentRound(ctx.season())
                        .map(round -> new Ctx(ctx.season(), round, ctx.prediction())))
                .flatMap(ctx -> resolveTargetRound(ctx.prediction(), ctx.season(), ctx.targetRound())
                        .map(round -> new Ctx(ctx.season(), round, ctx.prediction(), swapHelper.roundStatus(round))))
                .flatMap(ctx -> swapHelper
                        .validateEligibility(
                                ctx.prediction()
                                        .swapEligibility(ctx.targetRound().getPosition(), ctx.status(), now),
                                ctx.targetRound().getPosition(),
                                now)
                        .map(__ -> ctx))
                .flatMap(ctx -> swapHelper
                        .validateTeams(command, ctx.season(), ctx.prediction())
                        .map(teams -> performSwap(ctx, teams, now)));
    }

    private Either<SwapError, Round> resolveTargetRound(
            SeasonPrediction prediction, Season season, Round currentRound) {
        int target = prediction.swapTargetRound(currentRound.getPosition());
        if (target == currentRound.getPosition()) {
            return Either.right(currentRound);
        }
        return roundRepo
                .findBySeasonIdAndPosition(season.getId(), target)
                .map(Either::<SwapError, Round>right)
                .orElseGet(() -> Either.left(new SwapError.RoundNotFound(target, season.getId())));
    }

    private SwapResult performSwap(Ctx ctx, TeamSwap teams, Instant now) {
        SeasonPrediction prediction = ctx.prediction();
        prediction.swap(teams, ctx.targetRound().getPosition(), ctx.status(), now);
        SeasonPrediction saved = predictionRepo.save(prediction);

        return new SwapResult(
                true,
                now.plus(SeasonPrediction.SWAP_COOLDOWN),
                SeasonPrediction.SWAP_COOLDOWN.toMinutes() / 60.0,
                saved);
    }
}
