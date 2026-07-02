package com.ligitabl.api.rest.round.finalizeround;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.domain.StandingsCalculatorService;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.domain.service.ScoringEngine;
import com.ligitabl.model.repo.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizeRoundUseCase {

    private record FinalizationContext(Season season, Round round, Round currentRound, boolean recompute) {}

    private final HierarchyValidator hierarchyValidator;
    private final RoundRepo roundRepo;
    private final SeasonRepo seasonRepo;
    private final MatchRepo matchRepo;
    private final StandingsRepo standingsRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final RoundSubmissionRepo submissionRepo;
    private final RoundResultRepo resultRepo;
    private final StandingsCalculatorService standingsCalculator;
    private final ScoringEngine scoringEngine;
    private final Clock clock;

    @Transactional
    public Either<FinalizeRoundError, FinalizeRoundResult> execute(UUID seasonId) {
        return execute(FinalizeRoundCommand.of(seasonId));
    }

    @Transactional
    public Either<FinalizeRoundError, FinalizeRoundResult> execute(FinalizeRoundCommand command) {
        if (command == null || command.seasonId() == null) {
            return Either.left(new FinalizeRoundError.TransactionFailed("seasonId must not be null"));
        }

        log.info(
                "Starting round finalization for season: {} (roundPosition={}, recompute={})",
                command.seasonId(),
                command.roundPosition(),
                command.recompute());

        return getSeason(command.seasonId())
                .flatMap(season -> checkExplicitRefinalizeAllowed(season, command.roundPosition())
                        .map(__ -> season))
                .flatMap(season -> getCurrentRound(season).flatMap(currentRound -> getTargetRound(
                                season, currentRound, command.roundPosition())
                        .map(round -> new FinalizationContext(season, round, currentRound, command.recompute()))))
                .flatMap(ctx -> checkTargetNotAheadOfCurrent(ctx)
                        .flatMap(__ -> validateRoundReady(ctx))
                        .flatMap(__ -> executeFinalizationWorkflow(ctx)));
    }

    private Either<FinalizeRoundError, Season> getSeason(UUID seasonId) {
        return seasonRepo
                .findById(seasonId)
                .map(Either::<FinalizeRoundError, Season>right)
                .orElseGet(() -> Either.left(new FinalizeRoundError.SeasonNotFound(seasonId)));
    }

    // An explicit refinalize (roundPosition provided) is only valid while the season is in setup mode.
    private Either<FinalizeRoundError, Void> checkExplicitRefinalizeAllowed(Season season, Integer roundPosition) {
        if (roundPosition != null && !season.isInSetupMode()) {
            return Either.left(new FinalizeRoundError.NotInSetupMode(season.getId()));
        }
        return Either.right(null);
    }

    private Either<FinalizeRoundError, Round> getCurrentRound(Season season) {
        return hierarchyValidator.validateCurrentRound(season).mapLeft(__ ->
                (FinalizeRoundError) new FinalizeRoundError.RoundNotFound(season.getCurrentRoundId()));
    }

    private Either<FinalizeRoundError, Round> getTargetRound(Season season, Round currentRound, Integer roundPosition) {
        if (roundPosition == null) {
            return Either.right(currentRound);
        }
        return roundRepo
                .findBySeasonIdAndPosition(season.getId(), roundPosition)
                .map(Either::<FinalizeRoundError, Round>right)
                .orElseGet(() -> Either.left(new FinalizeRoundError.RoundNotFound(null)));
    }

    // A target round beyond the season's current round is never valid — the round hasn't been
    // reached yet, so there's nothing to (re)finalize. Equal is fine (normal finalize of the
    // current round); only strictly ahead is rejected.
    private Either<FinalizeRoundError, Void> checkTargetNotAheadOfCurrent(FinalizationContext ctx) {
        if (ctx.round().getPosition() > ctx.currentRound().getPosition()) {
            return Either.left(new FinalizeRoundError.RoundAheadOfCurrent(
                    ctx.round().getPosition(), ctx.currentRound().getPosition()));
        }
        return Either.right(null);
    }

    private Either<FinalizeRoundError, Void> validateRoundReady(FinalizationContext ctx) {
        if (ctx.round().isFinalized() && !ctx.recompute()) {
            return Either.left(
                    new FinalizeRoundError.AlreadyFinalized(ctx.round().getId()));
        }
        List<Match> matches = matchRepo.findByRoundId(ctx.round().getId());

        var obstructed = matches.stream().filter(m -> m.isBlocking()).toList();
        boolean allTerminalOrBlocking = matches.stream().allMatch(m -> m.isComplete() || m.isBlocking());
        if (!obstructed.isEmpty() && allTerminalOrBlocking) {
            var obstructedIds = obstructed.stream().map(m -> m.getId()).toList();
            return Either.left(new FinalizeRoundError.RoundObstructed(
                    ctx.round().getId(),
                    obstructedIds,
                    "Cannot finalize because some matches were cancelled or suspended."));
        }

        RoundStatus status = ctx.round().computeStatus(matches);
        if (ctx.recompute() && (status == RoundStatus.FINALIZED || status == RoundStatus.ADVANCED)) {
            return Either.right(null);
        }

        if (status != RoundStatus.COMPLETED) {
            return Either.left(new FinalizeRoundError.RoundNotReady(
                    ctx.round().getId(), "Round status is " + status + ", expected COMPLETED"));
        }

        return Either.right(null);
    }

    private Either<FinalizeRoundError, FinalizeRoundResult> executeFinalizationWorkflow(FinalizationContext ctx) {
        try {
            // Step 1: Calculate final standings
            var standingsResult = calculateFinalStandings(ctx);
            if (standingsResult.isLeft()) {
                return Either.left(standingsResult.getLeft());
            }
            Standings finalStandings = standingsResult.get();

            List<SeasonPrediction> predictions = predictionRepo.findBySeasonAndAtRoundNumberLessThanEqual(
                    ctx.season().getId(), ctx.round().getPosition());

            // Step 2: Create round submissions
            List<RoundSubmission> submissions = createRoundSubmissions(predictions, ctx);

            // Step 3: Calculate round results
            List<RoundResult> results = calculateRoundResults(predictions, submissions, finalStandings, ctx);

            // Step 4: Create next round standings (if not last round)
            boolean isLastRound = ctx.round().getPosition() >= ctx.season().getMaxRounds();
            if (!isLastRound) {
                createNextRoundStandings(ctx.round(), ctx.season(), finalStandings);
            }

            // Step 5: Mark round as finalized (do NOT advance season pointer here)
            if (!ctx.round().isFinalized()) {
                ctx.round().setFinalized(true);
                roundRepo.save(ctx.round());
                log.info("Round {} marked as finalized", ctx.round().getPosition());
            }

            // Step 5.5: Refinalize cascade — a recompute of a round before the current one means
            // every round's cumulative standings from here through the current round (inclusive)
            // are now stale. Mark them unfinalized/out-of-sync; nothing here re-scores them — the
            // admin walks forward refinalizing each in turn.
            if (ctx.recompute()) {
                markDownstreamOutOfSync(ctx);
            }

            // STEP 6: Send Notifications (TODO: implement async)

            Instant completedAt = clock.instant();
            log.info(
                    "Round finalization completed: round={}, submissions={}, results={}",
                    ctx.round().getPosition(),
                    submissions.size(),
                    results.size());

            return Either.right(new FinalizeRoundResult(
                    ctx.round().getId(),
                    ctx.round().getPosition(),
                    submissions.size(),
                    results.size(),
                    isLastRound,
                    completedAt));

        } catch (Exception e) {
            log.error("Round finalization failed", e);
            return Either.left(new FinalizeRoundError.TransactionFailed(e.getMessage()));
        }
    }

    private void markDownstreamOutOfSync(FinalizationContext ctx) {
        int refinalizedPosition = ctx.round().getPosition();
        int currentPosition = ctx.currentRound().getPosition();

        if (refinalizedPosition == currentPosition) {
            return;
        }

        int from = refinalizedPosition + 1;
        UUID seasonId = ctx.season().getId();
        roundRepo.markUnfinalizedBetween(seasonId, from, currentPosition);
        standingsRepo.markUnfinalisedBetween(seasonId, from, currentPosition);
        log.info("Refinalize cascade: marked rounds {}-{} unfinalized for season {}", from, currentPosition, seasonId);
    }

    // STEP 1: Calculate Final Standings
    private Either<FinalizeRoundError, Standings> calculateFinalStandings(FinalizationContext ctx) {
        try {
            Either<FinalizeRoundError, List<StandingsTeamRank>> calculation = standingsCalculator
                    .calculateRankings(ctx.season().getId(), ctx.round().getPosition())
                    .mapLeft(error -> new FinalizeRoundError.StandingsValidationFailed(error.toString()));

            if (calculation.isLeft()) {
                return Either.left(calculation.getLeft());
            }

            List<StandingsTeamRank> rankings = calculation.get();

            // Get or create standings record
            Standings standings = standingsRepo
                    .findBySeasonAndRoundPosition(
                            ctx.season().getId(), ctx.round().getPosition())
                    .orElseGet(() -> Standings.builder()
                            .seasonId(ctx.season().getId())
                            .roundPosition(ctx.round().getPosition())
                            .rankings(List.of())
                            .finalised(false)
                            .finalisedAt(null)
                            .build());

            standings.setRankings(rankings);
            standings.setFinalised(true);
            standings.setFinalisedAt(now());

            return Either.right(saveStandings(standings));

        } catch (Exception e) {
            return Either.left(new FinalizeRoundError.StandingsValidationFailed(e.getMessage()));
        }
    }

    // STEP 2: Create Round Submissions
    private List<RoundSubmission> createRoundSubmissions(List<SeasonPrediction> predictions, FinalizationContext ctx) {
        List<RoundSubmission> submissions = new ArrayList<>();

        for (SeasonPrediction prediction : predictions) {
            // Check if submission already exists (idempotency)
            Optional<RoundSubmission> existing = submissionRepo.findByUserAndSeasonAndRound(
                    prediction.getUserId(), ctx.season().getId(), ctx.round().getPosition());

            if (existing.isEmpty()) {
                RoundSubmission submission = RoundSubmission.builder()
                        .userId(prediction.getUserId())
                        .seasonId(ctx.season().getId())
                        .roundPosition(ctx.round().getPosition())
                        .rankings(new ArrayList<>(prediction.getCurrentRankings())) // snapshot
                        .seasonPredictionId(prediction.getId())
                        .build();

                submissions.add(submissionRepo.save(submission));
            } else {
                submissions.add(existing.get());
            }
        }

        log.info("Created {} round submissions", submissions.size());
        return submissions;
    }

    // STEP 3: Calculate Round Results
    private List<RoundResult> calculateRoundResults(
            List<SeasonPrediction> predictions,
            List<RoundSubmission> submissions,
            Standings finalStandings,
            FinalizationContext ctx) {
        Map<UUID, SeasonPrediction> predictionMap =
                predictions.stream().collect(Collectors.toMap(SeasonPrediction::getId, p -> p));
        List<RoundResult> results = new ArrayList<>();

        for (RoundSubmission submission : submissions) {
            SeasonPrediction prediction = predictionMap.get(submission.getSeasonPredictionId());

            if (prediction == null) {
                log.error("No prediction found for submission {}", submission.getId());
                continue;
            }
            // Check if result already exists (idempotency)
            Optional<RoundResult> existing = resultRepo.findByRoundSubmissionId(submission.getId());

            if (existing.isEmpty() || ctx.recompute()) {
                ScoringResult scoringResult = scoringEngine.calculateScore(
                        submission.getRankings(),
                        finalStandings.getRankings(),
                        ctx.season().getMaxHitPoints());

                int swapCount = countSwapsInRound(prediction, submission.getRoundPosition());

                RoundResult result = RoundResult.builder()
                        .roundSubmissionId(submission.getId())
                        .rankings(scoringResult.detailedRankings())
                        .totalScore(scoringResult.score())
                        .zeroesCount(scoringResult.zeroesCount())
                        .swapCount(swapCount)
                        .userViewed(false)
                        .build();

                results.add(resultRepo.save(result));
            } else {
                results.add(existing.get());
            }
        }

        return results;
    }

    // STEP 4: Create Next Round Standings
    private void createNextRoundStandings(Round currentRound, Season season, Standings finalStandings) {
        int nextRoundPosition = currentRound.getPosition() + 1;

        // Check if already exists (idempotency)
        Optional<Standings> existing = standingsRepo.findBySeasonAndRoundPosition(season.getId(), nextRoundPosition);

        if (existing.isEmpty()) {
            // Copy from finalized standings (cumulative stats)
            Standings nextStandings = Standings.builder()
                    .seasonId(season.getId())
                    .roundPosition(nextRoundPosition)
                    .rankings(new ArrayList<>(finalStandings.getRankings()))
                    .finalised(false)
                    .finalisedAt(null)
                    .build();

            saveStandings(nextStandings);
        }
    }

    private int countSwapsInRound(SeasonPrediction prediction, int roundPosition) {
        return prediction.getSwaps().stream()
                .filter(roundSwap -> roundSwap.getRound() == roundPosition)
                .mapToInt(roundSwap -> roundSwap.getChanges().size())
                .sum();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Standings saveStandings(Standings standings) {
        return standingsRepo.save(standings);
    }
}
