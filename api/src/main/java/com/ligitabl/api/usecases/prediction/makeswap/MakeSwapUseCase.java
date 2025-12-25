package com.ligitabl.api.usecases.prediction.makeswap;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.SwapChange;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MakeSwapUseCase {

    private final SeasonPredictionRepo predictionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final Clock clock;

    private static final Duration SWAP_COOLDOWN = Duration.ofHours(24);

    public Either<SwapError, SwapResult> execute(UUID userId, SwapCommand command) {
        return getCurrentSeason()
                .flatMap(season -> validateSeasonNotCompleted(season)
                        .flatMap(__ -> getPrediction(userId, season.getId()))
                        .flatMap(prediction -> validateSwapEligibility(prediction, season)
                                .flatMap(__ -> validateTeams(command, season, prediction))
                                .flatMap(teams -> performSwap(prediction, teams, season))));
    }

    private Either<SwapError, Season> getCurrentSeason() {
        return seasonRepo.findActiveSeason()
                .map(Either::<SwapError, Season>right)
                .orElseGet(() -> Either.left(new SwapError.NoPredictionFound(null, null)));
    }

    private Either<SwapError, Void> validateSeasonNotCompleted(Season season) {
        return season.isCompleted()
                ? Either.left(new SwapError.SeasonCompleted())
                : Either.right(null);
    }

    private Either<SwapError, SeasonPrediction> getPrediction(UUID userId, UUID seasonId) {
        return predictionRepo.findByUserAndSeason(userId, seasonId)
                .map(Either::<SwapError, SeasonPrediction>right)
                .orElseGet(() -> Either.left(new SwapError.NoPredictionFound(userId, seasonId)));
    }

    private Either<SwapError, Void> validateSwapEligibility(
            SeasonPrediction prediction,
            Season season
    ) {
        return validateRoundStatus(season)
                .flatMap(__ -> validateCooldown(prediction));
    }

    private Either<SwapError, Void> validateRoundStatus(Season season) {
        Round currentRound = roundRepo.findById(season.getCurrentRoundId())
                .orElseThrow(() -> new IllegalStateException("Current round not found"));

        RoundStatus status = currentRound.computeStatus();

        return status == RoundStatus.OPEN
                ? Either.right(null)
                : Either.left(new SwapError.RoundNotOpen(status.name()));
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

    private Either<SwapError, TeamPair> validateTeams(
            SwapCommand request,
            Season season,
            SeasonPrediction prediction
    ) {
        // Find teams in current rankings
        TeamRank teamA = prediction.getCurrentRankings().stream()
                .filter(t -> t.getCode().equals(request.teamACode()))
                .findFirst()
                .orElse(null);

        TeamRank teamB = prediction.getCurrentRankings().stream()
                .filter(t -> t.getCode().equals(request.teamBCode()))
                .findFirst()
                .orElse(null);

        if (teamA == null || teamB == null) {
            return Either.left(new SwapError.TeamsNotFound(
                    request.teamACode(),
                    request.teamBCode()
            ));
        }

        return Either.right(new TeamPair(teamA, teamB));
    }

    private Either<SwapError, SwapResult> performSwap(
            SeasonPrediction prediction,
            TeamPair teams,
            Season season
    ) {
        Instant now = clock.instant();
        Round currentRound = roundRepo.findById(season.getCurrentRoundId())
                .orElseThrow();

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
                String.format("%s:%d→%d", teams.teamA().getCode(),
                        teams.teamA().getPosition(), newTeamA.getPosition()),
                String.format("%s:%d→%d", teams.teamB().getCode(),
                        teams.teamB().getPosition(), newTeamB.getPosition())
        );

        // Update prediction
        prediction.setCurrentRankings(updatedRankings);
        prediction.addSwap(currentRound.getPosition(), change);
        prediction.setAtRoundNumber(currentRound.getPosition());
        prediction.setLastSwapAt(now);

        SeasonPrediction saved = predictionRepo.save(prediction);

        Instant nextSwapAt = now.plus(SWAP_COOLDOWN);
        double hoursUntilNext = 24.0;

        return Either.right(new SwapResult(
                true,
                nextSwapAt,
                hoursUntilNext,
                saved
        ));
    }

}
