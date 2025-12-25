package com.ligitabl.api.usecases.prediction.finalizeround;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.domain.service.ScoringEngine;
import com.ligitabl.model.domain.service.StandingsCalculator;
import com.ligitabl.model.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final StandingsCalculator standingsCalculator;
    private final ScoringEngine scoringEngine;
    private final Clock clock;

    @Transactional
    public Either<FinalizationError, FinalizationResult> execute(UUID seasonId) {
        log.info("Starting round finalization for season: {}", seasonId);

        return getSeason(seasonId)
                .flatMap(season -> getCurrentRound(season)
                        .flatMap(round -> validateRoundReady(round, season)
                                .flatMap(__ -> executeFinalizationWorkflow(round, season))));
    }

    private Either<FinalizationError, Season> getSeason(UUID seasonId) {
        return seasonRepo.findById(seasonId)
                .map(Either::<FinalizationError, Season>right)
                .orElseGet(() -> Either.left(
                        new FinalizationError.RoundNotReady(null, "Season not found")
                ));
    }

    private Either<FinalizationError, Round> getCurrentRound(Season season) {
        return roundRepo.findById(season.getCurrentRoundId())
                .map(Either::<FinalizationError, Round>right)
                .orElseGet(() -> Either.left(
                        new FinalizationError.RoundNotReady(null, "Current round not found")
                ));
    }

    private Either<FinalizationError, Void> validateRoundReady(Round round, Season season) {
        // Check round status
        List<Match> matches = matchRepo.findByRoundId(round.getId());
        RoundStatus status = round.computeStatus(matches);
        if (status != RoundStatus.FINALISED) {
            return Either.left(new FinalizationError.RoundNotReady(
                    round.getId(),
                    "Round status is " + status + ", expected FINALISED"
            ));
        }

        // Check for cancelled matches
        List<UUID> cancelledIds = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.CANCELLED)
                .map(Match::getId)
                .toList();

        if (!cancelledIds.isEmpty()) {
            return Either.left(new FinalizationError.CancelledMatchesPresent(cancelledIds));
        }

        return Either.right(null);
    }

    private Either<FinalizationError, FinalizationResult> executeFinalizationWorkflow(
            Round round,
            Season season
    ) {
        try {
            // Step 1: Calculate final standings
            Standings finalStandings = calculateFinalStandings(round, season)
                    .getOrElseThrow(err -> new RuntimeException(err.reason()));

            // Step 2: Create round submissions
            List<RoundSubmission> submissions = createRoundSubmissions(round, season);

            // Step 3: Calculate round results
            List<RoundResult> results = calculateRoundResults(
                    submissions,
                    finalStandings,
                    season
            );

            // Step 4: Create next round standings (if not last round)
            boolean isLastRound = round.getPosition() >= season.getMaxRounds();
            if (!isLastRound) {
                createNextRoundStandings(round, season, finalStandings);
            }

            // Step 5: Advance current round or complete season
            advanceRound(season, round, isLastRound);

            Instant completedAt = clock.instant();

            log.info("Round finalization completed: round={}, submissions={}, results={}",
                    round.getPosition(), submissions.size(), results.size());

            return Either.right(new FinalizationResult(
                    round.getId(),
                    round.getPosition(),
                    submissions.size(),
                    results.size(),
                    isLastRound,
                    completedAt
            ));

        } catch (Exception e) {
            log.error("Round finalization failed", e);
            return Either.left(new FinalizationError.TransactionFailed(e.getMessage()));
        }
    }

    // STEP 1: Calculate Final Standings
    private Either<FinalizationError, Standings> calculateFinalStandings(
            Round round,
            Season season
    ) {
        try {
            // Get all FINISHED matches up to this round
            List<Match> finishedMatches = matchRepo.findFinishedMatchesUpToRoundWithTeams(
                    season.getId(),
                    round.getPosition()
            );

            // Calculate rankings
            List<StandingsTeamRank> rankings = standingsCalculator.calculate(
                    finishedMatches,
                    season.getInitialRankings()
            );

            // Validate
            if (!standingsCalculator.validate(rankings, finishedMatches, season.getTotalTeams())) {
                return Either.left(new FinalizationError.StandingsValidationFailed(
                        "Matches played count mismatch"
                ));
            }

            // Get or create standings record
            Standings standings = standingsRepo
                                        .findBySeasonAndRound(season.getId(), round.getPosition())
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
            return Either.left(new FinalizationError.StandingsValidationFailed(e.getMessage()));
        }
    }

    // STEP 2: Create Round Submissions
    private List<RoundSubmission> createRoundSubmissions(Round round, Season season) {
        // Get all predictions that should participate
        List<SeasonPrediction> predictions = predictionRepo
                .findBySeasonAndAtRoundNumberLessThanEqual(
                        season.getId(),
                        round.getPosition()
                );

        List<RoundSubmission> submissions = new ArrayList<>();

        for (SeasonPrediction prediction : predictions) {
            // Check if submission already exists (idempotency)
            Optional<RoundSubmission> existing = submissionRepo
                    .findByUserAndSeasonAndRound(
                            prediction.getUserId(),
                            season.getId(),
                            round.getPosition()
                    );

            if (existing.isEmpty()) {
                RoundSubmission submission = RoundSubmission.builder()
                        .userId(prediction.getUserId())
                        .seasonId(season.getId())
                        .roundPosition(round.getPosition())
                        .rankings(prediction.getCurrentRankings())
                        .seasonPredictionId(prediction.getId())
                        .build();

                submissions.add(submissionRepo.save(submission));
            } else {
                submissions.add(existing.get());
            }
        }

        return submissions;
    }

    // STEP 3: Calculate Round Results
    private List<RoundResult> calculateRoundResults(
            List<RoundSubmission> submissions,
            Standings finalStandings,
            Season season
    ) {
        List<RoundResult> results = new ArrayList<>();

        for (RoundSubmission submission : submissions) {
            // Check if result already exists (idempotency)
            Optional<RoundResult> existing = resultRepo
                    .findByRoundSubmissionId(submission.getId());

            if (existing.isEmpty()) {
                ScoringResult scoringResult = scoringEngine.calculateScore(
                        submission.getRankings(),
                        finalStandings.getRankings(),
                        season.getMaxHitPoints()
                );

                RoundResult result = RoundResult.builder()
                        .roundSubmissionId(submission.getId())
                        .rankings(scoringResult.detailedRankings())
                        .score(scoringResult.score())
                        .zeroesCount(scoringResult.zeroesCount())
                        .swapCount(0)
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
    private void createNextRoundStandings(
            Round currentRound,
            Season season,
            Standings finalStandings
    ) {
        int nextRoundPosition = currentRound.getPosition() + 1;

        // Check if already exists (idempotency)
        Optional<Standings> existing = standingsRepo
                .findBySeasonAndRound(season.getId(), nextRoundPosition);

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

    // STEP 5: Advance Round or Complete Season
    private void advanceRound(Season season, Round currentRound, boolean isLastRound) {
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

        private OffsetDateTime now() {
                return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        }

        private Standings saveStandings(Standings standings) {
                if (standings.getId() == null) {
                        return standingsRepo.create(standings);
                }
                return standingsRepo.update(standings);
        }
}
