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

    private record FinalizationContext(Season season, Round round, boolean recompute, boolean autoAdvance) {}

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
    private final AdvanceCurrentRoundUseCase advanceCurrentRoundUseCase;

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
                "Starting round finalization for season: {} (recompute={}, autoAdvance={})",
                command.seasonId(),
                command.recompute(),
                command.autoAdvance());

        return getSeason(command.seasonId())
                .flatMap(season -> getCurrentRound(season)
                        .map(round ->
                                new FinalizationContext(season, round, command.recompute(), command.autoAdvance())))
                .flatMap(ctx -> validateRoundReady(ctx).flatMap(__ -> executeFinalizationWorkflow(ctx)
                        .flatMap(result -> maybeAutoAdvance(ctx, result))));
    }

    private Either<FinalizeRoundError, FinalizeRoundResult> maybeAutoAdvance(
            FinalizationContext ctx, FinalizeRoundResult result) {
        if (!ctx.autoAdvance()) {
            return Either.right(result);
        }

        var advance = advanceCurrentRoundUseCase.execute(new AdvanceCurrentRoundUseCase.AdvanceCurrentRoundCommand(
                ctx.season().getId(), result.roundId()));

        if (advance.isLeft()) {
            return Either.left(new FinalizeRoundError.TransactionFailed("Auto-advance failed: " + advance.getLeft()));
        }

        return Either.right(result);
    }

    private Either<FinalizeRoundError, Season> getSeason(UUID seasonId) {
        return seasonRepo
                .findById(seasonId)
                .map(Either::<FinalizeRoundError, Season>right)
                .orElseGet(() -> Either.left(new FinalizeRoundError.SeasonNotFound(seasonId)));
    }

    private Either<FinalizeRoundError, Round> getCurrentRound(Season season) {
        return hierarchyValidator.validateCurrentRound(season).mapLeft(__ ->
                (FinalizeRoundError) new FinalizeRoundError.RoundNotFound(season.getCurrentRoundId()));
    }

    private Either<FinalizeRoundError, Void> validateRoundReady(FinalizationContext ctx) {
        if (ctx.round().isFinalized() && !ctx.recompute()) {
            return Either.left(
                    new FinalizeRoundError.AlreadyFinalized(ctx.round().getId()));
        }
        List<Match> matches = matchRepo.findByRoundId(ctx.round().getId());

        var obstructed = matches.stream().filter(this::isBlocking).toList();
        boolean allTerminalOrBlocking = matches.stream().allMatch(m -> isComplete(m) || isBlocking(m));
        if (!obstructed.isEmpty() && allTerminalOrBlocking) {
            var obstructedIds = obstructed.stream().map(Match::getId).toList();
            return Either.left(new FinalizeRoundError.RoundObstructed(
                    ctx.round().getId(),
                    obstructedIds,
                    "Cannot finalize because some matches were cancelled or suspended."));
        }

        RoundStatus status = ctx.round().computeStatus(matches);
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

    private boolean isComplete(Match match) {
        if (match == null || match.getStatus() == null) {
            return false;
        }
        return match.getStatus() == MatchStatus.FINISHED || match.getStatus() == MatchStatus.POSTPONED;
    }

    private boolean isBlocking(Match match) {
        if (match == null || match.getStatus() == null) {
            return false;
        }
        return match.getStatus() == MatchStatus.CANCELLED || match.getStatus() == MatchStatus.SUSPENDED;
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
