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

@Component
@RequiredArgsConstructor
@Slf4j
public class CreatePredictionUseCase {

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
                .flatMap(__ -> checkNotAlreadyJoined(userId, season))
                .flatMap(__ -> validateSwapTeams(request, season))
                .flatMap(__ -> determineAtRoundNumber(season)
                        .flatMap(info -> createPredictionAndEntry(
                                userId, season, request, info.atRoundNumber(), info.currentRoundPosition()))));
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

    // Step 3: Check user hasn't already joined
    private Either<CreatePredictionError, Void> checkNotAlreadyJoined(UUID userId, Season season) {
        return predictionRepo
                .findByUserAndSeason(userId, season.getId())
                .map(existing -> Either.<CreatePredictionError, Void>left(
                        new CreatePredictionError.AlreadyJoined(existing.getId())))
                .orElseGet(() -> Either.right(null));
    }

    // Step 4: Validate the swap team codes (1-5 pairs required)
    private static final int MAX_INITIAL_SWAPS = 5;

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

    // Step 6: Create prediction and entry (transactional)
    private Either<CreatePredictionError, CreatePredictionResult> createPredictionAndEntry(
            UUID userId, Season season, CreatePredictionCommand request, int atRoundNumber, int currentRoundPosition) {
        var mainContestOpt = contestRepo.findById(season.getMainContestId());
        if (mainContestOpt.isEmpty()) {
            return Either.left(new CreatePredictionError.MainContestNotFound());
        }
        Contest mainContest = mainContestOpt.get();

        try {
            Instant now = clock.instant();
            List<TeamRank> currentRankings = new ArrayList<>(getPreviousRoundRankings(season, currentRoundPosition));
            List<SwapChange> swapChanges = new ArrayList<>();

            // Apply each swap sequentially from the baseline; record each as a SwapChange
            for (CreatePredictionCommand.SwapPair swap : request.swaps()) {
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
                        String.format("%s:%d\u2192%d", codeA, rankA.getPosition(), rankB.getPosition()),
                        String.format("%s:%d\u2192%d", codeB, rankB.getPosition(), rankA.getPosition())));
                currentRankings = applySwap(currentRankings, codeA, codeB);
            }

            RoundSwap initialRoundSwap = new RoundSwap(atRoundNumber, swapChanges);

            SeasonPrediction prediction = SeasonPrediction.builder()
                    .userId(userId)
                    .seasonId(season.getId())
                    .initialRankings(List.of()) // deprecated — will be removed in a future cleanup
                    .currentRankings(currentRankings)
                    .swaps(new ArrayList<>(List.of(initialRoundSwap)))
                    .lastSwapAt(null) // NOT set — next real swap is free immediately
                    .atRoundNumber(atRoundNumber)
                    .build();

            SeasonPrediction savedPrediction = predictionRepo.save(prediction);
            log.info("Created prediction {} for user {} at round {}", savedPrediction.getId(), userId, atRoundNumber);

            // Create Entry
            Entry entry = Entry.builder()
                    .userId(userId)
                    .contestId(mainContest.getId())
                    .joinedAt(now)
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
