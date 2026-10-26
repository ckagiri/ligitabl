package com.ligitabl.api.usecase.match.syncmatches;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TriggerRoundFinalizationUseCase {

    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final FinalizeRoundUseCase finalizeRoundUseCase;

    public Either<TriggerRoundFinalizationError, TriggerRoundFinalizationResult> execute(
            TriggerRoundFinalizationCommand command) {

        log.info("Checking if round can be finalized for competition: {}", command.competitionCode());

        return getActiveSeasonAndRound(command.competitionCode()).flatMap(this::executeFinalization);
    }

    private Either<TriggerRoundFinalizationError, RoundContext> getActiveSeasonAndRound(String competitionCode) {

        // Get active season
        var seasonOpt = seasonRepo.findActiveSeason(competitionCode);
        if (seasonOpt.isEmpty()) {
            return Either.left(new TriggerRoundFinalizationError.SeasonNotFound(competitionCode));
        }

        var season = seasonOpt.get();

        // Get current round
        var roundOpt = roundRepo.findById(season.getCurrentRoundId());
        if (roundOpt.isEmpty()) {
            return Either.left(new TriggerRoundFinalizationError.RoundNotFound(season.getCurrentRoundId()));
        }

        var round = roundOpt.get();

        return Either.right(new RoundContext(round));
    }

    private Either<TriggerRoundFinalizationError, TriggerRoundFinalizationResult> executeFinalization(
            RoundContext context) {

        log.info("Triggering finalization for round {}", context.round().getPosition());

        return finalizeRoundUseCase
                .execute(context.round.getSeasonId())
                .mapLeft(error ->
                        (TriggerRoundFinalizationError) new TriggerRoundFinalizationError.FinalizationFailed(error))
                .map(result -> new TriggerRoundFinalizationResult(
                        result.roundId(),
                        context.round().getPosition(),
                        true,
                        false,
                        "Round finalized successfully: " + result.submissionsCreated()
                                + " submissions, " + result.resultsCalculated()
                                + " results"));
    }

    private record RoundContext(Round round) {}
}
