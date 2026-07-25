package com.ligitabl.api.web.predictions.latestresult;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.error.ErrorMapper;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class GetLatestResultUseCase {
    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final RoundResultRepo roundResultRepo;
    private final RoundSupport roundSupport;
    private final LeaderboardRepo leaderboardRepo;
    private final ContestRepo contestRepo;
    private final CompetitionRepo competitionRepo;

    public Either<UseCaseError, Optional<LatestResultResponse>> execute(UUID userId) {
        return Either.catching(() -> buildResult(userId), ErrorMapper::toUseCaseError);
    }

    private Optional<LatestResultResponse> buildResult(UUID userId) {
        Season season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));

        Round currentRoundEntity = getCurrentRoundEntity(season);
        int currentRound = currentRoundEntity.getPosition();
        RoundStatus currentRoundStatus = roundSupport.resolveStatus(currentRoundEntity);

        int lastRound = season.getMaxRounds();
        boolean seasonCompleted = currentRoundStatus == RoundStatus.ADVANCED && currentRound == lastRound;

        // Only show banner when round is open, locked, completed, or the season has just completed.
        if (currentRoundStatus != RoundStatus.OPEN
                && currentRoundStatus != RoundStatus.LOCKED
                && currentRoundStatus != RoundStatus.COMPLETED
                && !seasonCompleted) {
            return Optional.empty();
        }

        // Once the season is ADVANCED past the last round there's no "next" round for currentRound
        // to have moved to, so it stays pinned at lastRound itself — the final round's own result,
        // not its predecessor's.
        int resultRound = seasonCompleted ? currentRound : currentRound - 1;
        if (resultRound < 1) {
            return Optional.empty();
        }

        Optional<RoundResult> roundResult = roundResultRepo.findByUserAndRound(userId, resultRound);
        if (roundResult.isEmpty()) {
            return Optional.empty();
        }

        RoundResult result = roundResult.get();
        if (result.isUserViewed()) {
            return Optional.empty();
        }

        return Optional.of(buildResponse(result, resultRound, userId, season, currentRound));
    }

    private LatestResultResponse buildResponse(
            RoundResult result, int round, UUID userId, Season season, int currentRound) {
        HitDistribution distribution = result.hitDistribution();

        // Calculate position and movement from leaderboard
        Integer position = null;
        Integer movement = null;
        String sprint = null;
        int sprintBest = 0;
        boolean isNewSprintBest = false;

        // Find the sprint for this round
        var competition = competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("Competition not found"));

        RoundSpan sprintPhase = competition.sprintForRound(round);
        sprint = sprintPhase.getCode();

        // Only calculate if we have a main contest
        if (season.getMainContestId() != null) {
            var contestOpt = contestRepo.findById(season.getMainContestId());
            if (contestOpt.isPresent()) {
                var contest = contestOpt.get();
                // Query leaderboard for the sprint up to the result round
                var leaderboardResponse = leaderboardRepo.computeLeaderboard(
                        contest.getId(),
                        season.getId(),
                        sprintPhase.getFrom(), // from = sprint start
                        round, // to = result round
                        userId,
                        0,
                        1, // just need userEntry
                        true);

                LeaderboardEntry userEntry = leaderboardResponse.userEntry();
                if (userEntry != null) {
                    position = userEntry.position();
                    movement = userEntry.movement();
                    sprintBest = userEntry.maxScore();

                    if (round > sprintPhase.getFrom()) {
                        var previousResponse = leaderboardRepo.computeLeaderboard(
                                contest.getId(),
                                season.getId(),
                                sprintPhase.getFrom(),
                                round - 1,
                                userId,
                                0,
                                1,
                                true);
                        LeaderboardEntry previousEntry = previousResponse.userEntry();
                        int previousSprintBest = previousEntry != null ? previousEntry.maxScore() : 0;
                        isNewSprintBest = result.getTotalScore() > previousSprintBest;
                    }
                }
            }
        }

        return new LatestResultResponse(
                round,
                result.getTotalScore(),
                position,
                movement,
                distribution,
                sprint,
                sprintPhase.getFrom(),
                sprintPhase.getTo(),
                sprintBest,
                isNewSprintBest,
                currentRound,
                season.getMaxRounds());
    }

    private Round getCurrentRoundEntity(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            throw new IllegalStateException("Season has no current round");
        }
        return roundRepo
                .findById(currentRoundId)
                .orElseThrow(() -> new IllegalStateException("Current round not found"));
    }
}
