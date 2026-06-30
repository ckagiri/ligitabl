package com.ligitabl.api.rest.prediction.createprediction;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles both normal in-season joining and pre-season registration (the "easter egg" — a
 * one-time 0-5 swap allowance available before predictions officially open).
 *
 * <p>Pre-season registrations are stored as a SeasonPrediction with atRoundNumber=0 and
 * initialRankings populated (the permanent marker that this user pre-registered). Once
 * predictions open, a later call to execute() for that same user finds the round-0 row and
 * updates it in place with the real atRoundNumber, rather than creating a duplicate or
 * rejecting with AlreadyJoined.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreatePredictionUseCase {

    private static final int ROUND_ZERO = 0;
    private static final int MAX_INITIAL_SWAPS = 5;

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final ContestRepo contestRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final EntryRepo entryRepo;
    private final StandingsRepo standingsRepo;
    private final Clock clock;

    @Transactional
    public Either<CreatePredictionError, CreatePredictionResult> execute(UUID userId, CreatePredictionCommand request) {
        log.info("User {} attempting to join contest", userId);

        return getActiveSeason().flatMap(season -> validateSeasonActive(season)
                .flatMap(__ -> resolveJoinPlan(userId, season))
                .flatMap(plan -> validateSwapTeams(request, season).flatMap(__ -> executeJoinPlan(
                        userId, season, request, plan))));
    }

    // Step 1: Get active season
    private Either<CreatePredictionError, Season> getActiveSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<CreatePredictionError, Season>right)
                .orElseGet(() -> Either.left(new CreatePredictionError.NotFound()));
    }

    // Step 2: Validate season is active
    private Either<CreatePredictionError, Void> validateSeasonActive(Season season) {
        if (season.isCompleted()) {
            return Either.left(new CreatePredictionError.Completed());
        }
        return Either.right(null);
    }

    // Step 3: Decide whether this is a fresh join, a fresh pre-season registration,
    // or a merge of an existing pre-season registration into a real entry.
    private sealed interface JoinPlan {
        record NewJoin() implements JoinPlan {}

        record NewPreSeasonRegistration() implements JoinPlan {}

        record MergePreSeasonRegistration(SeasonPrediction existing) implements JoinPlan {}
    }

    private Either<CreatePredictionError, JoinPlan> resolveJoinPlan(UUID userId, Season season) {
        return predictionRepo
                .findByUserAndSeason(userId, season.getId())
                .map(existing -> resolveExistingPrediction(season, existing))
                .orElseGet(() -> Either.right(
                        season.isPreSeason() ? new JoinPlan.NewPreSeasonRegistration() : new JoinPlan.NewJoin()));
    }

    private Either<CreatePredictionError, JoinPlan> resolveExistingPrediction(
            Season season, SeasonPrediction existing) {
        boolean isPreSeasonRegistrationRow = existing.getAtRoundNumber() == ROUND_ZERO;
        boolean predictionsNowOpen = !season.isPreSeason();

        if (isPreSeasonRegistrationRow && predictionsNowOpen) {
            return Either.right(new JoinPlan.MergePreSeasonRegistration(existing));
        }
        return Either.left(new CreatePredictionError.AlreadyJoined(existing.getId()));
    }

    private Either<CreatePredictionError, CreatePredictionResult> executeJoinPlan(
            UUID userId, Season season, CreatePredictionCommand request, JoinPlan plan) {
        return switch (plan) {
            case JoinPlan.NewJoin __ -> determineAtRoundNumber(season)
                    .flatMap(info -> createPredictionAndEntry(
                            userId, season, request, info.atRoundNumber(), info.currentRoundPosition()));
            case JoinPlan.NewPreSeasonRegistration __ -> registerPreSeason(userId, season, request);
            case JoinPlan.MergePreSeasonRegistration merge -> determineAtRoundNumber(season)
                    .flatMap(info -> mergePreSeasonRegistration(userId, season, request, merge.existing(), info.atRoundNumber()));
        };
    }

    // Step 4: Validate the swap team codes (0-5 pairs allowed)
    private Either<CreatePredictionError, Void> validateSwapTeams(CreatePredictionCommand cmd, Season season) {
        List<CreatePredictionCommand.SwapPair> swaps = cmd.swaps();

        if (swaps.size() > MAX_INITIAL_SWAPS) {
            return Either.left(new CreatePredictionError.TooManySwaps(swaps.size(), MAX_INITIAL_SWAPS));
        }

        Set<String> validCodes =
                season.getInitialRankings().stream().map(TeamRank::getCode).collect(Collectors.toSet());

        for (CreatePredictionCommand.SwapPair swap : swaps) {
            String codeA = swap.teamACode().toUpperCase();
            String codeB = swap.teamBCode().toUpperCase();

            if (codeA.equals(codeB)) {
                return Either.left(new CreatePredictionError.SameTeam());
            }
            if (!validCodes.contains(codeA)) {
                return Either.left(new CreatePredictionError.InvalidTeamCode(codeA));
            }
            if (!validCodes.contains(codeB)) {
                return Either.left(new CreatePredictionError.InvalidTeamCode(codeB));
            }
        }

        return Either.right(null);
    }

    // Step 5: Determine at_round_number
    private record RoundInfo(int atRoundNumber, int currentRoundPosition) {}

    private Either<CreatePredictionError, RoundInfo> determineAtRoundNumber(Season season) {
        var currentRoundOpt = roundRepo.findById(season.getCurrentRoundId());
        if (currentRoundOpt.isEmpty()) {
            return Either.left(new CreatePredictionError.CurrentRoundNotFound(season.getId()));
        }
        Round currentRound = currentRoundOpt.get();

        RoundStatus roundStatus;
        if (currentRound.isFinalized()) {
            roundStatus = RoundStatus.COMPLETED;
        } else {
            var matches = matchRepo.findByRoundId(currentRound.getId());
            roundStatus =
                    (matches == null || matches.isEmpty()) ? RoundStatus.OPEN : currentRound.computeStatus(matches);
        }

        int atRoundNumber;
        if (roundStatus == RoundStatus.OPEN) {
            atRoundNumber = currentRound.getPosition();
        } else {
            atRoundNumber = currentRound.getPosition() + 1;
        }

        // Check if season has ended
        if (atRoundNumber > season.getMaxRounds()) {
            return Either.left(new CreatePredictionError.Ended(currentRound.getPosition(), season.getMaxRounds()));
        }

        // Special case: Last round must be OPEN to join
        if (currentRound.getPosition() == season.getMaxRounds() && roundStatus != RoundStatus.OPEN) {
            return Either.left(new CreatePredictionError.Ended(currentRound.getPosition(), season.getMaxRounds()));
        }

        return Either.right(new RoundInfo(atRoundNumber, currentRound.getPosition()));
    }

    private record SwapResult(List<TeamRank> rankings, List<SwapChange> changes) {}

    private SwapResult applySwaps(List<TeamRank> baseline, List<CreatePredictionCommand.SwapPair> swaps, Instant now) {
        List<TeamRank> currentRankings = new ArrayList<>(baseline);
        List<SwapChange> swapChanges = new ArrayList<>();

        // Apply each swap sequentially from the baseline; record each as a SwapChange
        for (CreatePredictionCommand.SwapPair swap : swaps) {
            String codeA = swap.teamACode().toUpperCase();
            String codeB = swap.teamBCode().toUpperCase();
            TeamRank rankA = currentRankings.stream()
                    .filter(t -> t.getCode().equals(codeA))
                    .findFirst()
                    .orElseThrow();
            TeamRank rankB = currentRankings.stream()
                    .filter(t -> t.getCode().equals(codeB))
                    .findFirst()
                    .orElseThrow();
            swapChanges.add(new SwapChange(
                    now,
                    String.format("%s:%d→%d", codeA, rankA.getPosition(), rankB.getPosition()),
                    String.format("%s:%d→%d", codeB, rankB.getPosition(), rankA.getPosition())));
            currentRankings = applySwap(currentRankings, codeA, codeB);
        }

        return new SwapResult(currentRankings, swapChanges);
    }

    // Step 6a: Create prediction and entry for a normal (in-season) join
    private Either<CreatePredictionError, CreatePredictionResult> createPredictionAndEntry(
            UUID userId, Season season, CreatePredictionCommand request, int atRoundNumber, int currentRoundPosition) {
        var mainContestOpt = contestRepo.findById(season.getMainContestId());
        if (mainContestOpt.isEmpty()) {
            return Either.left(new CreatePredictionError.MainContestNotFound());
        }
        Contest mainContest = mainContestOpt.get();

        try {
            Instant now = clock.instant();
            SwapResult swapResult =
                    applySwaps(getPreviousRoundRankings(season, currentRoundPosition), request.swaps(), now);

            SeasonPrediction prediction = SeasonPrediction.builder()
                    .userId(userId)
                    .seasonId(season.getId())
                    .currentRankings(swapResult.rankings())
                    .swaps(new ArrayList<>(List.of(new RoundSwap(atRoundNumber, swapResult.changes()))))
                    .lastSwapAt(null) // NOT set — next real swap is free immediately
                    .atRoundNumber(atRoundNumber)
                    .build();

            SeasonPrediction savedPrediction = predictionRepo.save(prediction);
            log.info("Created prediction {} for user {} at round {}", savedPrediction.getId(), userId, atRoundNumber);

            Entry entry = Entry.builder()
                    .userId(userId)
                    .contestId(mainContest.getId())
                    .joinedAtRound(atRoundNumber)
                    .build();

            Entry savedEntry = entryRepo.save(entry);
            log.info(
                    "Created entry {} for user {} in default contest {}",
                    savedEntry.getId(),
                    userId,
                    mainContest.getId());

            String message = atRoundNumber == 1
                    ? "Welcome! Your prediction is active from Round 1"
                    : String.format("Welcome! Your prediction will be active from Round %d", atRoundNumber);

            return Either.right(
                    new CreatePredictionResult(savedPrediction.getId(), savedEntry.getId(), atRoundNumber, message));

        } catch (Exception e) {
            log.error("Failed to create prediction and entry", e);
            return Either.left(new CreatePredictionError.TransactionFailed(e.getMessage()));
        }
    }

    // Step 6b: Pre-season registration — the "easter egg". One-time 0-5 swap shot,
    // stored at atRoundNumber=0, excluded from leaderboard scoring (round > 0 filter).
    private Either<CreatePredictionError, CreatePredictionResult> registerPreSeason(
            UUID userId, Season season, CreatePredictionCommand request) {
        var mainContestOpt = contestRepo.findById(season.getMainContestId());
        if (mainContestOpt.isEmpty()) {
            return Either.left(new CreatePredictionError.MainContestNotFound());
        }
        Contest mainContest = mainContestOpt.get();

        try {
            Instant now = clock.instant();
            SwapResult swapResult = applySwaps(season.getInitialRankings(), request.swaps(), now);

            SeasonPrediction prediction = SeasonPrediction.builder()
                    .userId(userId)
                    .seasonId(season.getId())
                    .initialRankings(swapResult.rankings()) // permanent marker: this user pre-registered
                    .currentRankings(swapResult.rankings())
                    .swaps(new ArrayList<>(List.of(new RoundSwap(ROUND_ZERO, swapResult.changes()))))
                    .lastSwapAt(null)
                    .atRoundNumber(ROUND_ZERO)
                    .build();

            SeasonPrediction savedPrediction = predictionRepo.save(prediction);
            log.info(
                    "[PRE_SEASON_REGISTER] Created prediction {} for user {} with {} swaps",
                    savedPrediction.getId(),
                    userId,
                    swapResult.changes().size());

            Entry entry = Entry.builder()
                    .userId(userId)
                    .contestId(mainContest.getId())
                    .joinedAtRound(ROUND_ZERO)
                    .build();

            Entry savedEntry = entryRepo.save(entry);

            return Either.right(new CreatePredictionResult(
                    savedPrediction.getId(),
                    savedEntry.getId(),
                    ROUND_ZERO,
                    "You're registered! Your table is set for when predictions open."));

        } catch (Exception e) {
            log.error("Failed to create pre-season registration", e);
            return Either.left(new CreatePredictionError.TransactionFailed(e.getMessage()));
        }
    }

    // Step 6c: Merge an existing pre-season registration into a real entry once predictions open.
    // Updates the existing SeasonPrediction/Entry in place rather than creating duplicates.
    private Either<CreatePredictionError, CreatePredictionResult> mergePreSeasonRegistration(
            UUID userId, Season season, CreatePredictionCommand request, SeasonPrediction existing, int atRoundNumber) {
        var mainContestOpt = contestRepo.findById(season.getMainContestId());
        if (mainContestOpt.isEmpty()) {
            return Either.left(new CreatePredictionError.MainContestNotFound());
        }
        Contest mainContest = mainContestOpt.get();

        Entry entry = entryRepo.findByUserAndContest(userId, mainContest.getId()).orElse(null);
        if (entry == null) {
            return Either.left(new CreatePredictionError.MainContestNotFound());
        }

        try {
            Instant now = clock.instant();
            // Baseline is the pre-registration snapshot, not season-wide standings — this user's
            // starting point is whatever they set during pre-season registration.
            List<TeamRank> baseline = existing.getInitialRankings() != null
                    ? existing.getInitialRankings()
                    : existing.getCurrentRankings();
            SwapResult swapResult = applySwaps(baseline, request.swaps(), now);

            existing.setAtRoundNumber(atRoundNumber);
            existing.setCurrentRankings(swapResult.rankings());
            for (SwapChange change : swapResult.changes()) {
                existing.addSwap(atRoundNumber, change);
            }

            SeasonPrediction saved = predictionRepo.save(existing);
            log.info(
                    "[MERGE_PRE_SEASON_REGISTRATION] Updated prediction {} for user {} to atRoundNumber={}",
                    saved.getId(),
                    userId,
                    atRoundNumber);

            String message = atRoundNumber == 1
                    ? "Welcome! Your prediction is active from Round 1"
                    : String.format("Welcome! Your prediction will be active from Round %d", atRoundNumber);

            return Either.right(new CreatePredictionResult(saved.getId(), entry.getId(), atRoundNumber, message));

        } catch (Exception e) {
            log.error("Failed to merge pre-season registration into real entry", e);
            return Either.left(new CreatePredictionError.TransactionFailed(e.getMessage()));
        }
    }

    private List<TeamRank> getPreviousRoundRankings(Season season, int currentRoundPosition) {
        if (currentRoundPosition < 3) {
            return season.getInitialRankings();
        }
        return standingsRepo
                .findBySeasonAndRoundPosition(season.getId(), currentRoundPosition - 2)
                .map(standings -> standings.getRankings().stream()
                        .map(StandingsTeamRank::getRanking)
                        .toList())
                .orElseGet(season::getInitialRankings);
    }

    private List<TeamRank> applySwap(List<TeamRank> base, String codeA, String codeB) {
        List<TeamRank> result = new ArrayList<>(base);
        int idxA = IntStream.range(0, result.size())
                .filter(i -> result.get(i).getCode().equals(codeA))
                .findFirst()
                .orElseThrow();
        int idxB = IntStream.range(0, result.size())
                .filter(i -> result.get(i).getCode().equals(codeB))
                .findFirst()
                .orElseThrow();
        TeamRank a = result.get(idxA);
        TeamRank b = result.get(idxB);
        result.set(idxA, a.withPosition(b.getPosition()));
        result.set(idxB, b.withPosition(a.getPosition()));
        return result;
    }
}
