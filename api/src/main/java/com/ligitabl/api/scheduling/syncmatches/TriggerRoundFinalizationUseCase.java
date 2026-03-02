package com.ligitabl.api.scheduling.syncmatches;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.api.shared.Either;
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

    public record TriggerFinalizationCommand(String competitionCode) {}

    public sealed interface TriggerFinalizationError {
        record SeasonNotFound(String competitionCode) implements TriggerFinalizationError {}

        record RoundNotFound(UUID roundId) implements TriggerFinalizationError {}

        record FinalizationFailed(FinalizeRoundError error) implements TriggerFinalizationError {}
    }

    public record TriggerFinalizationResult(
            UUID roundId, int roundPosition, boolean finalized, boolean blocked, String message) {}

    public Either<TriggerFinalizationError, TriggerFinalizationResult> execute(TriggerFinalizationCommand command) {

        log.info("Checking if round can be finalized for competition: {}", command.competitionCode());

        return getActiveSeasonAndRound(command.competitionCode()).flatMap(this::executeFinalization);
    }

    private Either<TriggerFinalizationError, RoundContext> getActiveSeasonAndRound(String competitionCode) {

        // Get active season
        var seasonOpt = seasonRepo.findActiveSeason(competitionCode);
        if (seasonOpt.isEmpty()) {
            return Either.left(new TriggerFinalizationError.SeasonNotFound(competitionCode));
        }

        var season = seasonOpt.get();

        // Get current round
        var roundOpt = roundRepo.findById(season.getCurrentRoundId());
        if (roundOpt.isEmpty()) {
            return Either.left(new TriggerFinalizationError.RoundNotFound(season.getCurrentRoundId()));
        }

        var round = roundOpt.get();

        return Either.right(new RoundContext(round));
    }

    private Either<TriggerFinalizationError, TriggerFinalizationResult> executeFinalization(RoundContext context) {

        log.info("Triggering finalization for round {}", context.round().getPosition());

        return finalizeRoundUseCase
                .execute(context.round.getSeasonId())
                .mapLeft(error -> (TriggerFinalizationError) new TriggerFinalizationError.FinalizationFailed(error))
                .map(result -> new TriggerFinalizationResult(
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
