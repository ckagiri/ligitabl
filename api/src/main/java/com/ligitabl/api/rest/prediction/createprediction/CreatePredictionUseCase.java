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
    private final Clock clock;

    @Transactional
    public Either<CreatePredictionError, CreatePredictionResult> execute(UUID userId, CreatePredictionCommand request) {
        log.info("User {} attempting to join contest", userId);

        return getActiveSeason().flatMap(season -> validateSeasonActive(season)
                .flatMap(__ -> checkNotAlreadyJoined(userId, season))
                .flatMap(__ -> validateSwapTeams(request, season))
                .flatMap(__ -> determineAtRoundNumber(season)
                        .flatMap(atRoundNumber -> createPredictionAndEntry(userId, season, request, atRoundNumber))));
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

    // Step 4: Validate the swap team codes
    private Either<CreatePredictionError, Void> validateSwapTeams(CreatePredictionCommand cmd, Season season) {
        String codeA = cmd.teamACode().toUpperCase();
        String codeB = cmd.teamBCode().toUpperCase();

        if (codeA.equals(codeB)) {
            return Either.left(new CreatePredictionError.SameTeam());
        }

        Set<String> validCodes = season.getInitialRankings().stream()
                .map(TeamRank::getCode)
                .collect(Collectors.toSet());

        if (!validCodes.contains(codeA)) {
            return Either.left(new CreatePredictionError.InvalidTeamCode(codeA));
        }
        if (!validCodes.contains(codeB)) {
            return Either.left(new CreatePredictionError.InvalidTeamCode(codeB));
        }

        return Either.right(null);
    }

    // Step 5: Determine at_round_number
    private Either<CreatePredictionError, Integer> determineAtRoundNumber(Season season) {
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

        return Either.right(atRoundNumber);
    }

    // Step 6: Create prediction and entry (transactional)
    private Either<CreatePredictionError, CreatePredictionResult> createPredictionAndEntry(
            UUID userId, Season season, CreatePredictionCommand request, int atRoundNumber) {
        var mainContestOpt = contestRepo.findById(season.getMainContestId());
        if (mainContestOpt.isEmpty()) {
            return Either.left(new CreatePredictionError.MainContestNotFound());
        }
        Contest mainContest = mainContestOpt.get();

        try {
            String codeA = request.teamACode().toUpperCase();
            String codeB = request.teamBCode().toUpperCase();
            List<TeamRank> baseline = season.getInitialRankings();
            List<TeamRank> currentRankings = applySwap(baseline, codeA, codeB);

            // Build initial swap change — recorded before saving (single save)
            TeamRank baseA = baseline.stream()
                    .filter(t -> t.getCode().equals(codeA))
                    .findFirst()
                    .orElseThrow();
            TeamRank baseB = baseline.stream()
                    .filter(t -> t.getCode().equals(codeB))
                    .findFirst()
                    .orElseThrow();
            Instant now = clock.instant();
            SwapChange initialChange = new SwapChange(
                    now,
                    String.format("%s:%d\u2192%d", codeA, baseA.getPosition(), baseB.getPosition()),
                    String.format("%s:%d\u2192%d", codeB, baseB.getPosition(), baseA.getPosition()));
            RoundSwap initialRoundSwap = new RoundSwap(atRoundNumber, new ArrayList<>(List.of(initialChange)));

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
