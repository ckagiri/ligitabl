package com.ligitabl.api.rest.prediction.makeswap;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.rest.prediction.shared.SwapHelper;
import com.ligitabl.api.shared.Either;
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

    private static final Duration SWAP_COOLDOWN = Duration.ofHours(24);

    private record Ctx(Season season, Round targetRound, SeasonPrediction prediction, TeamPair teams) {
        Ctx(Season season, Round targetRound, SeasonPrediction prediction) {
            this(season, targetRound, prediction, null);
        }
    }

    public Either<SwapError, SwapResult> execute(UUID userId, SwapCommand command) {
        return swapHelper.getCurrentSeason()
                .flatMap(season -> swapHelper.validateSeasonNotCompleted(season).map(__ -> season))
                .flatMap(season -> swapHelper.getPrediction(userId, season.getId())
                        .map(p -> new Ctx(season, null, p)))
                .flatMap(ctx -> getCurrentRound(ctx.season())
                        .map(round -> new Ctx(ctx.season(), round, ctx.prediction())))
                .flatMap(ctx -> resolveTargetRound(ctx.prediction(), ctx.season(), ctx.targetRound())
                        .map(round -> new Ctx(ctx.season(), round, ctx.prediction())))
                .flatMap(ctx -> swapHelper.validateRoundOpen(ctx.targetRound()).map(__ -> ctx))
                .flatMap(ctx -> validateCooldown(ctx.prediction(), ctx.targetRound()).map(__ -> ctx))
                .flatMap(ctx -> swapHelper.validateTeams(command, ctx.season(), ctx.prediction(),
                        ctx.prediction().getCurrentRankings())
                        .map(teams -> new Ctx(ctx.season(), ctx.targetRound(), ctx.prediction(), teams)))
                .flatMap(ctx -> performSwap(ctx.prediction(), ctx.teams(), ctx.targetRound()));
    }

    private Either<SwapError, Round> getCurrentRound(Season season) {
        return roundRepo
                .findById(season.getCurrentRoundId())
                .map(Either::<SwapError, Round>right)
                .orElseGet(() -> Either.left(new SwapError.RoundNotOpen(RoundStatus.UNKNOWN.name())));
    }

    private Either<SwapError, Round> resolveTargetRound(
            SeasonPrediction prediction, Season season, Round currentRound) {
        int predictionRound = prediction.getAtRoundNumber();
        int nextPosition = currentRound.getPosition() + 1;

        if (predictionRound == nextPosition) {
            return roundRepo
                    .findBySeasonIdAndPosition(season.getId(), predictionRound)
                    .map(Either::<SwapError, Round>right)
                    .orElseGet(() -> Either.left(new SwapError.RoundNotFound(predictionRound, season.getId())));
        }

        return Either.right(currentRound);
    }

    private Either<SwapError, Void> validateCooldown(SeasonPrediction prediction, Round currentRound) {
        if (prediction.getLastSwapAt() == null) {
            return Either.right(null); // First swap bonus — no wait required
        }

        // Once first swap bonus is spent, the opening window must be used before cooldown swaps
        if (prediction.getOpeningCommittedRound() != currentRound.getPosition()) {
            return Either.left(new SwapError.UseOpeningWindowFirst(currentRound.getPosition()));
        }

        Instant now = clock.instant();
        Instant nextSwapAt = prediction.getLastSwapAt().plus(SWAP_COOLDOWN);

        if (now.isBefore(nextSwapAt)) {
            double hoursRemaining = Duration.between(now, nextSwapAt).toMinutes() / 60.0;
            return Either.left(new SwapError.CooldownActive(nextSwapAt, hoursRemaining));
        }

        return Either.right(null);
    }

    private Either<SwapError, SwapResult> performSwap(
            SeasonPrediction prediction, TeamPair teams, Round targetRound) {
        Instant now = clock.instant();

        List<TeamRank> updatedRankings = new ArrayList<>(prediction.getCurrentRankings());
        SwapChange change = swapHelper.applySwap(updatedRankings, teams.teamA(), teams.teamB(), now);

        prediction.setCurrentRankings(updatedRankings);
        prediction.addSwap(targetRound.getPosition(), change);
        prediction.setLastSwapAt(now);
        // First swap bonus: consume the opening window so it isn't shown after the bonus is used
        if (prediction.getOpeningCommittedRound() != targetRound.getPosition()) {
            prediction.setOpeningCommittedRound(targetRound.getPosition());
        }

        SeasonPrediction saved = predictionRepo.save(prediction);

        Instant nextSwapAt = now.plus(SWAP_COOLDOWN);
        double hoursUntilNext = 24.0;

        return Either.right(new SwapResult(true, nextSwapAt, hoursUntilNext, saved));
    }
}
