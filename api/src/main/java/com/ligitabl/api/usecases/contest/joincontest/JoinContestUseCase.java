package com.ligitabl.api.usecases.contest.joincontest;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

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
public class JoinContestUseCase {

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final ContestRepo contestRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final EntryRepo entryRepo;
    private final Clock clock;

    @Transactional
    public Either<JoinContestError, JoinContestResult> execute(UUID userId, JoinContestCommand request) {
        log.info("User {} attempting to join contest", userId);

        return getActiveSeason().flatMap(season -> validateSeasonActive(season)
                .flatMap(__ -> checkNotAlreadyJoined(userId, season))
                .flatMap(__ -> validateRankings(request, season))
                .flatMap(rankings -> determineAtRoundNumber(season)
                        .flatMap(atRoundNumber -> createPredictionAndEntry(userId, season, rankings, atRoundNumber))));
    }

    // Step 1: Get active season
    private Either<JoinContestError, Season> getActiveSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<JoinContestError, Season>right)
                .orElseGet(() -> Either.left(new JoinContestError.SeasonNotFound()));
    }

    // Step 2: Validate season is active
    private Either<JoinContestError, Void> validateSeasonActive(Season season) {
        if (season.isCompleted()) {
            return Either.left(new JoinContestError.SeasonCompleted());
        }
        return Either.right(null);
    }

    // Step 3: Check user hasn't already joined
    private Either<JoinContestError, Void> checkNotAlreadyJoined(UUID userId, Season season) {
        return predictionRepo
                .findByUserAndSeason(userId, season.getId())
                .map(existing ->
                        Either.<JoinContestError, Void>left(new JoinContestError.AlreadyJoined(existing.getId())))
                .orElseGet(() -> Either.right(null));
    }

    // Step 4: Validate rankings structure
    private Either<JoinContestError, List<TeamRank>> validateRankings(JoinContestCommand request, Season season) {
        return validateTeamCount(request, season)
                .flatMap(__ -> validateNoDuplicatePositions(request))
                .flatMap(__ -> validateNoDuplicateCodes(request))
                .flatMap(__ -> validateTeamCodesExist(request, season))
                .map(__ -> convertToTeamRanks(request));
    }

    private Either<JoinContestError, Void> validateTeamCount(JoinContestCommand request, Season season) {
        int provided = request.rankings().size();
        int required = season.getTotalTeams();

        if (provided != required) {
            return Either.left(new JoinContestError.InvalidTeamCount(provided, required));
        }
        return Either.right(null);
    }

    private Either<JoinContestError, Void> validateNoDuplicatePositions(JoinContestCommand request) {
        List<Integer> positions = request.rankings().stream()
                .map(JoinContestCommand.TeamRankRequest::position)
                .toList();

        List<Integer> duplicates = positions.stream()
                .filter(p -> Collections.frequency(positions, p) > 1)
                .distinct()
                .toList();

        if (!duplicates.isEmpty()) {
            return Either.left(new JoinContestError.DuplicatePositions(duplicates));
        }
        return Either.right(null);
    }

    private Either<JoinContestError, Void> validateNoDuplicateCodes(JoinContestCommand request) {
        List<String> codes = request.rankings().stream()
                .map(JoinContestCommand.TeamRankRequest::code)
                .map(String::toUpperCase)
                .toList();

        List<String> duplicates = codes.stream()
                .filter(c -> Collections.frequency(codes, c) > 1)
                .distinct()
                .toList();

        if (!duplicates.isEmpty()) {
            return Either.left(new JoinContestError.DuplicateTeamCodes(duplicates));
        }
        return Either.right(null);
    }

    private Either<JoinContestError, Void> validateTeamCodesExist(JoinContestCommand request, Season season) {
        List<String> requestedCodes = request.rankings().stream()
                .map(JoinContestCommand.TeamRankRequest::code)
                .map(String::toUpperCase)
                .toList();

        // Get all valid team codes from season's initial rankings
        Set<String> validCodes =
                season.getInitialRankings().stream().map(TeamRank::getCode).collect(Collectors.toSet());

        List<String> invalidCodes = requestedCodes.stream()
                .filter(code -> !validCodes.contains(code))
                .toList();

        if (!invalidCodes.isEmpty()) {
            return Either.left(new JoinContestError.InvalidTeamCodes(invalidCodes));
        }

        return Either.right(null);
    }

    private List<TeamRank> convertToTeamRanks(JoinContestCommand request) {
        return request.rankings().stream()
                .map(r -> new TeamRank(r.code().toUpperCase(), r.position()))
                .sorted(Comparator.comparingInt(TeamRank::getPosition))
                .toList();
    }

    // Step 5: Determine at_round_number
    private Either<JoinContestError, Integer> determineAtRoundNumber(Season season) {
        Round currentRound = roundRepo
                .findById(season.getCurrentRoundId())
                .orElseThrow(() -> new IllegalStateException("Current round not found"));

        RoundStatus roundStatus;
        if (currentRound.isFinalized()) {
            roundStatus = RoundStatus.FINALISED;
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
            return Either.left(new JoinContestError.SeasonEnded(currentRound.getPosition(), season.getMaxRounds()));
        }

        // Special case: Last round must be OPEN to join
        if (currentRound.getPosition() == season.getMaxRounds() && roundStatus != RoundStatus.OPEN) {
            return Either.left(new JoinContestError.SeasonEnded(currentRound.getPosition(), season.getMaxRounds()));
        }

        return Either.right(atRoundNumber);
    }

    // Step 6: Create prediction and entry (transactional)
    private Either<JoinContestError, JoinContestResult> createPredictionAndEntry(
            UUID userId, Season season, List<TeamRank> rankings, int atRoundNumber) {
        try {
            // Create SeasonPrediction
            SeasonPrediction prediction = SeasonPrediction.builder()
                    .userId(userId)
                    .seasonId(season.getId())
                    .initialRankings(new ArrayList<>(rankings)) // Immutable copy
                    .currentRankings(new ArrayList<>(rankings)) // Will change with swaps
                    .swaps(new ArrayList<>()) // Empty initially
                    .lastSwapAt(null) // No swaps yet
                    .atRoundNumber(atRoundNumber)
                    .build();

            SeasonPrediction savedPrediction = predictionRepo.save(prediction);
            log.info("Created prediction {} for user {} at round {}", savedPrediction.getId(), userId, atRoundNumber);

            // Get default contest
            Contest defaultContest = contestRepo
                    .findById(season.getMainContestId())
                    .orElseThrow(() -> new IllegalStateException("Default contest not found"));

            // Create Entry
            Entry entry = Entry.builder()
                    .userId(userId)
                    .contestId(defaultContest.getId())
                    .joinedAt(clock.instant())
                    .build();

            Entry savedEntry = entryRepo.save(entry);
            log.info(
                    "Created entry {} for user {} in default contest {}",
                    savedEntry.getId(),
                    userId,
                    defaultContest.getId());

            String message = atRoundNumber == 1
                    ? "Welcome! Your prediction is active from Round 1"
                    : String.format("Welcome! Your prediction will be active from Round %d", atRoundNumber);

            return Either.right(
                    new JoinContestResult(savedPrediction.getId(), savedEntry.getId(), atRoundNumber, message));

        } catch (Exception e) {
            log.error("Failed to create prediction and entry", e);
            return Either.left(new JoinContestError.TransactionFailed(e.getMessage()));
        }
    }
}
