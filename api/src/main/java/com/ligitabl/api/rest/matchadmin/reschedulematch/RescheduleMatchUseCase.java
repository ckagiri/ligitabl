package com.ligitabl.api.rest.matchadmin.reschedulematch;

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
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.rest.shared.HierarchyValidator.HierarchyContext;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RescheduleMatchUseCase implements UseCase<RescheduleMatchCommand, Either<UseCaseError, RescheduleResult>> {

    private final MatchRepo matchRepo;
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
        return resolveTargetRound(matchCtx.context().season(), newRoundPosition).flatMap(targetRound -> {
            RescheduleContext ctx = new RescheduleContext(
                    matchCtx.context().season(), matchCtx.context().round(), targetRound, matchCtx.match());

            if (ctx.season().isInSetupMode()) {
                return Either.right(ctx);
            }

            return validateLiveMode(ctx.match(), ctx.currentRound(), ctx.targetRound())
                    .map(ignored -> ctx);
        });
    }

    private Either<UseCaseError, Void> validateLiveMode(Match match, Round currentRound, Round targetRound) {
        if (match.getStatus() == MatchStatus.SUSPENDED) {
            return Either.left(UseCaseErrors.validation(
                    "Cannot reschedule SUSPENDED match directly. Transition to POSTPONED first."));
        }
        if (match.getStatus() == MatchStatus.LIVE) {
            return Either.left(UseCaseErrors.validation("Cannot reschedule LIVE match"));
        }
        if (match.getStatus() == MatchStatus.FINISHED) {
            return Either.left(UseCaseErrors.validation("Cannot reschedule FINISHED match"));
        }

        if (targetRound.getPosition() < currentRound.getPosition()) {
            return Either.left(UseCaseErrors.validation(String.format(
                    "Cannot reschedule to past round %d (current is %d)",
                    targetRound.getPosition(), currentRound.getPosition())));
        }

        if (targetRound.isFinalized()) {
            return Either.left(UseCaseErrors.validation(
                    String.format("Round %d is already finalized", targetRound.getPosition())));
        }

        return Either.right(null);
    }

    private Either<UseCaseError, RescheduleResult> performReschedule(
            RescheduleContext ctx, RescheduleMatchCommand cmd) {
        try {
            Match match = ctx.match();
            Instant now = clock.instant();

            MatchStatus oldStatus = match.getStatus();
            int fromRound = ctx.currentRound().getPosition();
            int toRound = ctx.targetRound().getPosition();

            match.rescheduleToRound(ctx.targetRound().getId(), ctx.season().isInSetupMode());

            Match saved = matchRepo.save(match);

            return Either.right(RescheduleResult.builder()
                    .matchId(saved.getId())
                    .matchSlug(saved.getSlug())
                    .oldStatus(oldStatus)
                    .newStatus(saved.getStatus())
                    .fromRound(fromRound)
                    .toRound(toRound)
                    .wasPostponed(saved.isWasPostponed())
                    .timestamp(now)
                    .build());

        } catch (Exception e) {
            return Either.left(UseCaseErrors.fromException(e));
        }
    }

    private record MatchContext(HierarchyContext context, Match match) {}

    private record RescheduleContext(Season season, Round currentRound, Round targetRound, Match match) {}
}
