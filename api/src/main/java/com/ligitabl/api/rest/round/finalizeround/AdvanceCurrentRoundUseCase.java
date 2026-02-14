package com.ligitabl.api.rest.round.finalizeround;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdvanceCurrentRoundUseCase {

    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final Clock clock;
    private final HierarchyValidator hierarchyValidator;

    public record AdvanceCurrentRoundCommand(UUID seasonId, UUID finalizedRoundId) {}

    public sealed interface AdvanceCurrentRoundError {
        record SeasonNotFound(UUID seasonId) implements AdvanceCurrentRoundError {}

        record RoundNotFound(UUID roundId) implements AdvanceCurrentRoundError {}

        record NextRoundNotFound(UUID seasonId, int position) implements AdvanceCurrentRoundError {}

        record InvalidState(String message) implements AdvanceCurrentRoundError {}
    }

    public record AdvanceCurrentRoundResult(boolean advanced, boolean seasonCompleted, Integer newRoundPosition) {}

    @Transactional
    public Either<AdvanceCurrentRoundError, AdvanceCurrentRoundResult> execute(AdvanceCurrentRoundCommand command) {
        if (command == null || command.seasonId() == null || command.finalizedRoundId() == null) {
            return Either.left(new AdvanceCurrentRoundError.InvalidState("seasonId and finalizedRoundId are required"));
        }

        Season season = seasonRepo.findById(command.seasonId()).orElse(null);
        if (season == null) {
            return Either.left(new AdvanceCurrentRoundError.SeasonNotFound(command.seasonId()));
        }

        var currentRoundResult = hierarchyValidator.validateCurrentRound(season).mapLeft(__ ->
                (AdvanceCurrentRoundError) new AdvanceCurrentRoundError.RoundNotFound(season.getCurrentRoundId()));

        if (currentRoundResult.isLeft()) {
            return Either.left(currentRoundResult.getLeft());
        }

        Round currentRound = currentRoundResult.get();
        if (!currentRound.getId().equals(command.finalizedRoundId())) {
            return Either.left(new AdvanceCurrentRoundError.InvalidState(
                    "Cannot advance: finalizedRoundId does not match season currentRoundId"));
        }

        if (!currentRound.isFinalized()) {
            return Either.left(
                    new AdvanceCurrentRoundError.InvalidState("Cannot advance: current round is not finalized"));
        }

        int currentPosition = currentRound.getPosition();
        boolean isLastRound = currentPosition >= season.getMaxRounds();

        if (isLastRound) {
            if (!season.isCompleted()) {
                season.setCompleted(true);
                season.setCompletedAt(now());
                seasonRepo.save(season);
                log.info("Season marked completed: seasonId={}", season.getId());
            }

            return Either.right(new AdvanceCurrentRoundResult(false, true, null));
        }

        int nextPosition = currentPosition + 1;
        var nextRoundOpt = roundRepo.findBySeasonIdAndPosition(season.getId(), nextPosition);
        if (nextRoundOpt.isEmpty()) {
            return Either.left(new AdvanceCurrentRoundError.NextRoundNotFound(season.getId(), nextPosition));
        }

        UUID nextRoundId = nextRoundOpt.get().getId();
        season.setCurrentRoundId(nextRoundId);
        seasonRepo.save(season);

        return Either.right(new AdvanceCurrentRoundResult(true, false, nextPosition));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
