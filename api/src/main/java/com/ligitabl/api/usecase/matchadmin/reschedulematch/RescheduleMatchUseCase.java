package com.ligitabl.api.usecase.matchadmin.reschedulematch;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.api.usecase.shared.HierarchyValidator.HierarchyContext;
import com.ligitabl.model.domain.league.Match;
import com.ligitabl.model.domain.league.MatchStatus;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RescheduleMatchUseCase implements UseCase<RescheduleMatchCommand, Either<UseCaseError, RescheduleResult>> {

    private final MatchRepo matchRepo;
    private final RoundRepo roundRepo;
    private final StandingsRepo standingsRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final Clock clock;

    @Override
    @Transactional
    public Either<UseCaseError, RescheduleResult> execute(RescheduleMatchCommand cmd) {
        log.info("Executing RescheduleMatch: slug={}, toRound={}", cmd.getMatchSlug(), cmd.getNewRoundPosition());

        String competitionIdentifier =
                cmd.getCompetitionIdentifier().orElseGet(competitionDefaults::defaultCompetitionSlug);

        return hierarchyValidator
                .resolveHierarchy(competitionIdentifier, cmd.getRoundPosition())
                .flatMap(context -> findMatch(context.round().getId(), cmd.getMatchSlug())
                        .map(match -> new MatchContext(context, match)))
                .flatMap(matchCtx -> validateReschedule(matchCtx, cmd.getNewRoundPosition()))
                .flatMap(rescheduleCtx -> performReschedule(rescheduleCtx, cmd));
    }

    private Either<UseCaseError, Match> findMatch(UUID roundId, String matchSlug) {
        return requireFound(
                matchRepo.findByRoundIdAndSlug(roundId, matchSlug), UseCaseErrors.notFound("Match", "slug", matchSlug));
    }

    private Either<UseCaseError, Round> resolveTargetRound(Season season, int position) {
        if (position < 1) {
            return Either.left(UseCaseErrors.validation("Round position must be at least 1"));
        }
        return hierarchyValidator.validateRound(season.getId(), position);
    }

    private Either<UseCaseError, RescheduleContext> validateReschedule(MatchContext matchCtx, int newRoundPosition) {
        Season season = matchCtx.context().season();

        return resolveTargetRound(season, newRoundPosition)
                .flatMap(targetRound -> hierarchyValidator
                        .validateCurrentRound(season)
                        .map(seasonCurrentRound -> new RescheduleContext(
                                season, matchCtx.context().round(), targetRound, seasonCurrentRound, matchCtx.match())))
                .flatMap(ctx -> validateOperationalMode(ctx).map(ignored -> ctx));
    }

    // SUSPENDED/CANCELLED/LIVE are blocked in both modes. Setup mode relaxes FINISHED and the
    // past-round/finalized-round limits on SCHEDULED/POSTPONED so admins can rearrange historical
    // fixtures, but a FINISHED match still can't move into an unplayed future round.
    private Either<UseCaseError, Void> validateOperationalMode(RescheduleContext ctx) {
        Match match = ctx.match();
        Round seasonCurrentRound = ctx.seasonCurrentRound();
        Round targetRound = ctx.targetRound();
        boolean setupMode = ctx.season().isInSetupMode();

        if (match.getStatus() == MatchStatus.SUSPENDED) {
            return Either.left(UseCaseErrors.validation(
                    "Cannot reschedule SUSPENDED match directly. Transition to CANCELLED first."));
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            return Either.left(UseCaseErrors.validation(
                    "Cannot reschedule CANCELLED match directly. Transition to POSTPONED first."));
        }
        if (match.getStatus() == MatchStatus.LIVE) {
            return Either.left(UseCaseErrors.validation("Cannot reschedule LIVE match"));
        }

        if (match.getStatus() == MatchStatus.FINISHED) {
            if (!setupMode) {
                return Either.left(UseCaseErrors.validation("Cannot reschedule FINISHED match"));
            }
            if (targetRound.getPosition() > seasonCurrentRound.getPosition()) {
                return Either.left(UseCaseErrors.validation(String.format(
                        "Cannot reschedule a FINISHED match to future round %d (current is %d)",
                        targetRound.getPosition(), seasonCurrentRound.getPosition())));
            }
            return Either.right(null);
        }

        // Remaining statuses: SCHEDULED, POSTPONED.
        if (!setupMode) {
            if (targetRound.getPosition() < seasonCurrentRound.getPosition()) {
                return Either.left(UseCaseErrors.validation(String.format(
                        "Cannot reschedule to past round %d (current is %d)",
                        targetRound.getPosition(), seasonCurrentRound.getPosition())));
            }

            if (targetRound.isFinalized()) {
                return Either.left(UseCaseErrors.validation(
                        String.format("Round %d is already finalized", targetRound.getPosition())));
            }
        }

        return Either.right(null);
    }

    private Either<UseCaseError, RescheduleResult> performReschedule(
            RescheduleContext ctx, RescheduleMatchCommand cmd) {
        try {
            Match match = ctx.match();
            Instant now = clock.instant();

            MatchStatus oldStatus = match.getStatus();
            int fromRound = ctx.sourceRound().getPosition();
            int toRound = ctx.targetRound().getPosition();

            match.rescheduleToRound(ctx.targetRound().getId(), ctx.season().isInSetupMode());

            Match saved = matchRepo.save(match);

            markOutOfSyncIfMovedToPastRound(ctx, fromRound, toRound);

            return Either.right(RescheduleResult.builder()
                    .matchId(saved.getId())
                    .matchSlug(saved.getSlug())
                    .oldStatus(oldStatus)
                    .newStatus(saved.getStatus())
                    .fromRound(fromRound)
                    .toRound(toRound)
                    .wasPostponed(saved.wasPostponed())
                    .timestamp(now)
                    .build());

        } catch (Exception e) {
            return Either.left(UseCaseErrors.fromException(e));
        }
    }

    // Setup mode only. Moving a match into a round before the current one makes the finalized
    // standings covering it stale, whatever the match's status: mark them unfinalized, as
    // FinalizeRoundUseCase's refinalize cascade does, and don't recompute.
    private void markOutOfSyncIfMovedToPastRound(RescheduleContext ctx, int fromRound, int toRound) {
        int currentPosition = ctx.seasonCurrentRound().getPosition();
        if (toRound >= currentPosition) {
            return; // not moved into the past
        }

        int from = Math.min(fromRound, toRound);
        UUID seasonId = ctx.season().getId();
        roundRepo.markUnfinalizedBetween(seasonId, from, currentPosition);
        standingsRepo.markUnfinalisedBetween(seasonId, from, currentPosition);
        log.info(
                "Reschedule cascade: marked rounds {}-{} unfinalized for season {} (match moved {} -> {})",
                from,
                currentPosition,
                seasonId,
                fromRound,
                toRound);
    }

    private record MatchContext(HierarchyContext context, Match match) {}

    private record RescheduleContext(
            Season season, Round sourceRound, Round targetRound, Round seasonCurrentRound, Match match) {}
}
