package com.ligitabl.api.rest.prediction.makeswap;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MakeSwapUseCase {

    private final CompetitionDefaults competitionDefaults;
    private final SeasonPredictionRepo predictionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final Clock clock;

    private static final Duration SWAP_COOLDOWN = Duration.ofHours(24);

    public Either<SwapError, SwapResult> execute(UUID userId, SwapCommand command) {
        return getCurrentSeason().flatMap(season -> validateSeasonNotCompleted(season)
            .flatMap(__ -> getPrediction(userId, season.getId()))
            .flatMap(prediction -> validateSwapEligibility(prediction, season)
                .flatMap(targetRound -> validateTeams(command, season, prediction)
                    .flatMap(teams -> performSwap(prediction, teams, season, targetRound)))));
    }

    private Either<SwapError, Season> getCurrentSeason() {
        return seasonRepo
                .findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<SwapError, Season>right)
                .orElseGet(() -> Either.left(new SwapError.NoPredictionFound(null, null)));
    }

    private Either<SwapError, Void> validateSeasonNotCompleted(Season season) {
        return season.isCompleted() ? Either.left(new SwapError.SeasonCompleted()) : Either.right(null);
    }

    private Either<SwapError, SeasonPrediction> getPrediction(UUID userId, UUID seasonId) {
        return predictionRepo
                .findByUserAndSeason(userId, seasonId)
                .map(Either::<SwapError, SeasonPrediction>right)
                .orElseGet(() -> Either.left(new SwapError.NoPredictionFound(userId, seasonId)));
    }

    private Either<SwapError, Round> validateSwapEligibility(SeasonPrediction prediction, Season season) {
        return validateRoundStatus(prediction, season).flatMap(round -> validateCooldown(prediction).map(__ -> round));
    }

    private Either<SwapError, Round> validateRoundStatus(SeasonPrediction prediction, Season season) {
        Round currentRound = roundRepo
                .findById(season.getCurrentRoundId())
                .orElseThrow(() -> new IllegalStateException("Current round not found"));

        return resolveTargetRound(prediction, season, currentRound).flatMap(targetRound -> {
            RoundStatus status;
            if (targetRound.isFinalized()) {
                status = RoundStatus.COMPLETED;
            } else {
                var matches = matchRepo.findByRoundId(targetRound.getId());
                status = (matches == null || matches.isEmpty())
                        ? RoundStatus.OPEN
                        : targetRound.computeStatus(matches);
            }

            return status == RoundStatus.OPEN
                    ? Either.right(targetRound)
                    : Either.left(new SwapError.RoundNotOpen(status.name()));
        });
    }

    private Either<SwapError, Round> resolveTargetRound(
            SeasonPrediction prediction, Season season, Round currentRound) {
        int currentPosition = currentRound.getPosition();
        int nextPosition = currentPosition + 1;
        int predictionRound = prediction.getAtRoundNumber();

        if (predictionRound == nextPosition) {
            return roundRepo
                    .findBySeasonIdAndPosition(season.getId(), predictionRound)
                    .map(Either::<SwapError, Round>right)
                    .orElseGet(() -> Either.left(new SwapError.RoundNotFound(predictionRound, season.getId())));
        }

        return Either.right(currentRound);
    }

    private Either<SwapError, Void> validateCooldown(SeasonPrediction prediction) {
        if (prediction.getLastSwapAt() == null) {
            return Either.right(null); // First swap after submission
        }

        Instant now = clock.instant();
        Instant nextSwapAt = prediction.getLastSwapAt().plus(SWAP_COOLDOWN);

        if (now.isBefore(nextSwapAt)) {
            double hoursRemaining = Duration.between(now, nextSwapAt).toMinutes() / 60.0;
            return Either.left(new SwapError.CooldownActive(nextSwapAt, hoursRemaining));
        }

        return Either.right(null);
    }

    private Either<SwapError, TeamPair> validateTeams(SwapCommand request, Season season, SeasonPrediction prediction) {
        String teamACode = request.teamACode().toUpperCase();
        String teamBCode = request.teamBCode().toUpperCase();

        List<TeamRank> initialRankings =
                season.getInitialRankings() != null ? season.getInitialRankings() : prediction.getInitialRankings();

        Set<String> validCodes = initialRankings == null
                ? Set.of()
                : initialRankings.stream().map(TeamRank::getCode).collect(Collectors.toSet());

        if (!validCodes.contains(teamACode)) {
            return Either.left(new SwapError.InvalidTeamCode(teamACode));
        }
        if (!validCodes.contains(teamBCode)) {
            return Either.left(new SwapError.InvalidTeamCode(teamBCode));
        }

        // Find teams in current rankings
        TeamRank teamA = prediction.getCurrentRankings().stream()
                .filter(t -> t.getCode().equals(teamACode))
                .findFirst()
                .orElse(null);

        TeamRank teamB = prediction.getCurrentRankings().stream()
                .filter(t -> t.getCode().equals(teamBCode))
                .findFirst()
                .orElse(null);

        if (teamA == null || teamB == null) {
            return Either.left(new SwapError.TeamsNotFound(teamACode, teamBCode));
        }

        return Either.right(new TeamPair(teamA, teamB));
    }

    private Either<SwapError, SwapResult> performSwap(
            SeasonPrediction prediction, TeamPair teams, Season season, Round targetRound) {
        Instant now = clock.instant();

        // Swap positions in current_rankings
        List<TeamRank> updatedRankings = new ArrayList<>(prediction.getCurrentRankings());
        int indexA = updatedRankings.indexOf(teams.teamA());
        int indexB = updatedRankings.indexOf(teams.teamB());

        TeamRank newTeamA = teams.teamA().withPosition(teams.teamB().getPosition());
        TeamRank newTeamB = teams.teamB().withPosition(teams.teamA().getPosition());

        updatedRankings.set(indexA, newTeamA);
        updatedRankings.set(indexB, newTeamB);

        // Build swap history entry
        SwapChange change = new SwapChange(
                now,
                String.format("%s:%d→%d", teams.teamA().getCode(), teams.teamA().getPosition(), newTeamA.getPosition()),
                String.format(
                        "%s:%d→%d", teams.teamB().getCode(), teams.teamB().getPosition(), newTeamB.getPosition()));

        // Update prediction
        prediction.setCurrentRankings(updatedRankings);
        prediction.addSwap(targetRound.getPosition(), change);
        prediction.setAtRoundNumber(targetRound.getPosition());
        prediction.setLastSwapAt(now);

        SeasonPrediction saved = predictionRepo.save(prediction);

        Instant nextSwapAt = now.plus(SWAP_COOLDOWN);
        double hoursUntilNext = 24.0;

        return Either.right(new SwapResult(true, nextSwapAt, hoursUntilNext, saved));
    }
}
