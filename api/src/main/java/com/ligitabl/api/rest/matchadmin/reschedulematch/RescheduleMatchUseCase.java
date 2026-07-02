package com.ligitabl.api.rest.matchadmin.reschedulematch;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.rest.shared.HierarchyValidator.HierarchyContext;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
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
                .flatMap(targetRound -> hierarchyValidator.validateCurrentRound(season).map(seasonCurrentRound ->
                        new RescheduleContext(
                                season, matchCtx.context().round(), targetRound, seasonCurrentRound, matchCtx.match())))
                .flatMap(ctx -> validateOperationalMode(ctx).map(ignored -> ctx));
    }

    // SUSPENDED/CANCELLED/LIVE are always blocked, in both modes — an actively-live or
    // mid-transition match can't be rescheduled regardless of setup mode. FINISHED, and the
    // past-round/finalized-round restrictions for SCHEDULED/POSTPONED, are relaxed in setup mode
    // so admins can rearrange historical fixture data (see RescheduleMatchUseCase javadoc / task
    // notes) — but a FINISHED match still can't be pushed into an unplayed future round.
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

    // Only reachable in setup mode (validateOperationalMode blocks any past-round move otherwise).
    // Landing in a round before the season's current one means whatever finalized standings
    // included that round range are now stale — mirrors FinalizeRoundUseCase's refinalize cascade:
    // mark unfinalized, don't auto-recompute. Applies regardless of the match's status: the common
    // case is moving a not-yet-played match into the past to backfill it, then editing its
    // score/status afterward — by then standings are already correctly flagged out of sync.
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
