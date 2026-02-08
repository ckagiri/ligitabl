package com.ligitabl.api.web.predictions.latestresult;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.error.ErrorMapper;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.MatchRepo;
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
    private final MatchRepo matchRepo;

    public Either<UseCaseError, Optional<LatestResultResponse>> execute(UUID userId) {
        return Either.catching(() -> buildResult(userId), ErrorMapper::toUseCaseError);
    }

    private Optional<LatestResultResponse> buildResult(UUID userId) {
        Season season = seasonRepo
                .findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));

        Round currentRoundEntity = getCurrentRoundEntity(season);
        int currentRound = currentRoundEntity.getPosition();
        String roundState = resolveRoundState(currentRoundEntity);

        // Only show banner when round is open, locked, or completed
        if (!roundState.equals(RoundStatus.OPEN.name())
                && !roundState.equals(RoundStatus.LOCKED.name())
                && !roundState.equals(RoundStatus.COMPLETED.name())) {
            return Optional.empty();
        }

        int resultRound = currentRound - 1;
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

        return Optional.of(buildResponse(result, resultRound));
    }

    private LatestResultResponse buildResponse(RoundResult result, int round) {
        HitDistribution distribution = calculateHitDistribution(result);

        return new LatestResultResponse(
                round,
                result.getTotalScore(),
                null, // position - not implemented yet
                null, // movement - not implemented yet
                distribution);
    }

    private HitDistribution calculateHitDistribution(RoundResult result) {
        int perfect = 0;
        int closeCalls = 0;
        int nearMisses = 0;
        int bigMisses = 0;

        for (ResultTeamRank ranking : result.getRankings()) {
            int hit = ranking.getHit();
            if (hit == 0) {
                perfect++;
            } else if (hit <= 2) {
                closeCalls++;
            } else if (hit <= 5) {
                nearMisses++;
            } else {
                bigMisses++;
            }
        }

        return new HitDistribution(perfect, closeCalls, nearMisses, bigMisses);
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

    private String resolveRoundState(Round round) {
        if (round == null) {
            return RoundStatus.UNKNOWN.name();
        }
        if (round.isFinalized()) {
            return RoundStatus.FINALIZED.name();
        }
        var matches = matchRepo.findByRoundId(round.getId());
        if (matches == null || matches.isEmpty()) {
            return RoundStatus.OPEN.name();
        }
        return round.computeStatus(matches).name();
    }
}
