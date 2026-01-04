package com.ligitabl.api.usecases.sync;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecases.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.usecases.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.repo.MatchRepo;
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
    private final MatchRepo matchRepo;
    private final FinalizeRoundUseCase finalizeRoundUseCase;
    private final AdminNotificationService adminNotificationService;

    public record TriggerFinalizationCommand(String competitionCode) {}

    public sealed interface TriggerFinalizationError {
        record CompetitionNotFound(String code) implements TriggerFinalizationError {}

        record SeasonNotFound(String competitionCode) implements TriggerFinalizationError {}

        record RoundNotFound(UUID roundId) implements TriggerFinalizationError {}

        record BlockedByMatches(List<UUID> blockingMatchIds, String reason) implements TriggerFinalizationError {}

        record FinalizationFailed(FinalizeRoundError error) implements TriggerFinalizationError {}
    }

    public record TriggerFinalizationResult(
            UUID roundId, int roundPosition, boolean finalized, boolean blocked, String message) {}

    public Either<TriggerFinalizationError, TriggerFinalizationResult> execute(TriggerFinalizationCommand command) {

        log.info("Checking if round can be finalized for competition: {}", command.competitionCode());

        return getActiveSeasonAndRound(command.competitionCode())
                .flatMap(this::checkBlockingMatches)
                .flatMap(this::executeFinalization);
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
        var matches = matchRepo.findByRoundId(round.getId());

        return Either.right(new RoundContext(round, matches));
    }

    private Either<TriggerFinalizationError, RoundContext> checkBlockingMatches(RoundContext context) {

        var blockingMatches = context.matches().stream()
                .filter(m -> m.getStatus() == MatchStatus.CANCELLED || m.getStatus() == MatchStatus.SUSPENDED)
                .toList();

        if (!blockingMatches.isEmpty()) {
            log.warn("Round finalization blocked by {} matches in CANCELLED/SUSPENDED status", blockingMatches.size());

            // Send admin notification
            var matchIds = blockingMatches.stream().map(Match::getId).toList();

            var matchDetails = blockingMatches.stream()
                    .map(m -> String.format(
                            "- Match ID: %s, Status: %s, Matchday: %d", m.getId(), m.getStatus(), m.getMatchday()))
                    .toList();

            adminNotificationService.notifyBlockedFinalization(
                    context.round().getId(), context.round().getPosition(), matchIds, matchDetails);

            return Either.left(new TriggerFinalizationError.BlockedByMatches(
                    matchIds,
                    blockingMatches.size() + " matches in CANCELLED/SUSPENDED status require admin resolution"));
        }

        log.info("No blocking matches found, proceeding with finalization");
        return Either.right(context);
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

    private record RoundContext(Round round, List<Match> matches) {}
}
