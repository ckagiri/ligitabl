package com.ligitabl.api.usecases.round.finalizeround;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.domain.StandingsCalculatorService;
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
        log.info("Starting round finalization for season: {}", seasonId);

        return getSeason(seasonId)
                .flatMap(season -> getCurrentRound(season).flatMap(round -> validateRoundReady(round, season)
                        .flatMap(__ -> executeFinalizationWorkflow(round, season))));
    }

    private Either<FinalizeRoundError, Season> getSeason(UUID seasonId) {
        return seasonRepo
                .findById(seasonId)
                .map(Either::<FinalizeRoundError, Season>right)
                .orElseGet(() -> Either.left(new FinalizeRoundError.SeasonNotFound(seasonId)));
    }

    private Either<FinalizeRoundError, Round> getCurrentRound(Season season) {
        return roundRepo
                .findById(season.getCurrentRoundId())
                .map(Either::<FinalizeRoundError, Round>right)
                .orElseGet(() -> Either.left(new FinalizeRoundError.RoundNotFound(season.getCurrentRoundId())));
    }

    private Either<FinalizeRoundError, Void> validateRoundReady(Round round, Season season) {
        if (round.isFinalized()) {
            return Either.left(new FinalizeRoundError.AlreadyFinalized(round.getId()));
        }
        List<Match> matches = matchRepo.findByRoundId(round.getId());
        RoundStatus status = round.computeStatus(matches);
        if (status != RoundStatus.FINALISED) {
            return Either.left(new FinalizeRoundError.RoundNotReady(
                    round.getId(), "Round status is " + status + ", expected FINALISED"));
        }

        return Either.right(null);
    }

    private Either<FinalizeRoundError, FinalizeRoundResult> executeFinalizationWorkflow(Round round, Season season) {
        try {
            // Step 1: Calculate final standings
            Standings finalStandings =
                    calculateFinalStandings(round, season).getOrElseThrow(err -> new RuntimeException(err.toString()));

            List<SeasonPrediction> predictions =
                    predictionRepo.findBySeasonAndAtRoundNumberLessThanEqual(season.getId(), round.getPosition());

            // Step 2: Create round submissions
            List<RoundSubmission> submissions = createRoundSubmissions(predictions, round, season);

            // Step 3: Calculate round results
            List<RoundResult> results = calculateRoundResults(predictions, submissions, finalStandings, season);

            // Step 4: Create next round standings (if not last round)
            boolean isLastRound = round.getPosition() >= season.getMaxRounds();
            if (!isLastRound) {
                createNextRoundStandings(round, season, finalStandings);
            }

            // Step 5: Advance current round or complete season
            advanceCurrentRound(season, round, isLastRound);

            // STEP 6: Send Notifications (TODO: implement async)

            Instant completedAt = clock.instant();
            log.info(
                    "Round finalization completed: round={}, submissions={}, results={}",
                    round.getPosition(),
                    submissions.size(),
                    results.size());

            return Either.right(new FinalizeRoundResult(
                    round.getId(), round.getPosition(), submissions.size(), results.size(), isLastRound, completedAt));

        } catch (Exception e) {
            log.error("Round finalization failed", e);
            return Either.left(new FinalizeRoundError.TransactionFailed(e.getMessage()));
        }
    }

    // STEP 1: Calculate Final Standings
    private Either<FinalizeRoundError, Standings> calculateFinalStandings(Round round, Season season) {
        try {
            Either<FinalizeRoundError, List<StandingsTeamRank>> calculation = standingsCalculator
                    .calculateRankings(season.getId(), round.getPosition())
                    .mapLeft(error -> new FinalizeRoundError.StandingsValidationFailed(error.toString()));

            if (calculation.isLeft()) {
                return Either.left(calculation.getLeft());
            }

            List<StandingsTeamRank> rankings = calculation.get();

            // Get or create standings record
            Standings standings = standingsRepo
                    .findBySeasonAndRoundPosition(season.getId(), round.getPosition())
                    .orElseGet(() -> Standings.builder()
                            .seasonId(season.getId())
                            .roundPosition(round.getPosition())
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
    private List<RoundSubmission> createRoundSubmissions(
            List<SeasonPrediction> predictions, Round round, Season season) {
        List<RoundSubmission> submissions = new ArrayList<>();

        for (SeasonPrediction prediction : predictions) {
            // Check if submission already exists (idempotency)
            Optional<RoundSubmission> existing = submissionRepo.findByUserAndSeasonAndRound(
                    prediction.getUserId(), season.getId(), round.getPosition());

            if (existing.isEmpty()) {
                RoundSubmission submission = RoundSubmission.builder()
                        .userId(prediction.getUserId())
                        .seasonId(season.getId())
                        .roundPosition(round.getPosition())
                        .rankings(new ArrayList<>(prediction.getInitialRankings())) // snapshot
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
            Season season) {
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

            if (existing.isEmpty()) {
                ScoringResult scoringResult = scoringEngine.calculateScore(
                        submission.getRankings(), finalStandings.getRankings(), season.getMaxHitPoints());

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

    // STEP 5: Finalize & Advance CurrentRound or Complete Season
    private void advanceCurrentRound(Season season, Round currentRound, boolean isLastRound) {
        // first finalize current round
        currentRound.setFinalized(true);
        roundRepo.save(currentRound);

        log.info("Round {} marked as finalized", currentRound.getPosition());

        if (isLastRound) {
            season.setCompleted(true);
            season.setCompletedAt(now());
            log.info("Season completed: {}", season.getId());
        } else {
            Round nextRound = roundRepo
                    .findBySeasonIdAndPosition(season.getId(), currentRound.getPosition() + 1)
                    .orElseThrow(() -> new IllegalStateException("Next round not found"));

            season.setCurrentRoundId(nextRound.getId());
            log.info("Advanced to round: {}", nextRound.getPosition());
        }

        seasonRepo.save(season);
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
