package com.ligitabl.api.usecases.match.reschedulematch;

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
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RescheduleMatchUseCase implements UseCase<RescheduleMatchCommand, Either<UseCaseError, RescheduleResult>> {

    private final MatchRepo matchRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final Clock clock;

    @Override
    @Transactional
    public Either<UseCaseError, RescheduleResult> execute(RescheduleMatchCommand cmd) {
        log.info("Executing RescheduleMatch: slug={}, toRound={}", cmd.getMatchSlug(), cmd.getNewRoundPosition());

        return resolveHierarchy(cmd)
            .flatMap(context -> findMatch(context.currentRound().getId(), cmd.getMatchSlug())
                .map(match -> new MatchContext(context, match)))
            .flatMap(matchCtx -> validateReschedule(matchCtx, cmd.getNewRoundPosition()))
            .flatMap(rescheduleCtx -> performReschedule(rescheduleCtx, cmd));
    }

    private Either<UseCaseError, HierarchyContext> resolveHierarchy(RescheduleMatchCommand cmd) {
        String competitionIdentifier = cmd.getCompetitionIdentifier().orElseGet(competitionDefaults::defaultCompetitionSlug);

        return hierarchyValidator.validateCompetition(competitionIdentifier)
                .flatMap(this::getActiveSeason)
                .flatMap(season -> resolveRound(season, cmd)
                    .map(round -> new HierarchyContext(season, round)));
    }

    private Either<UseCaseError, Season> getActiveSeason(Competition competition) {
        UUID activeSeasonId = competition.getActiveSeasonId();
        if (activeSeasonId == null) {
            return Either.left(UseCaseErrors.validation("Competition has no active season"));
        }

        return requireFound(seasonRepo.findById(activeSeasonId), UseCaseErrors.notFound("Season", activeSeasonId));
    }

    private Either<UseCaseError, Round> resolveRound(Season season, RescheduleMatchCommand cmd) {
        if (cmd.isCurrentRound()) {
            UUID currentRoundId = season.getCurrentRoundId();
            if (currentRoundId == null) {
                return Either.left(UseCaseErrors.validation("Season has no current round"));
            }
            return requireFound(roundRepo.findById(currentRoundId), UseCaseErrors.notFound("Round", currentRoundId));
        }

        return cmd.getRoundPositionAsNumber()
                .map(pos -> hierarchyValidator.validateRound(season.getId(), pos))
                .orElseGet(() -> Either.left(UseCaseErrors.validation("Invalid round position format")));
    }

    private Either<UseCaseError, Match> findMatch(UUID roundId, String matchSlug) {
        return requireFound(
                matchRepo.findByRoundIdAndSlug(roundId, matchSlug),
                UseCaseErrors.notFound("Match", "slug", matchSlug));
    }

    private Either<UseCaseError, Round> resolveTargetRound(Season season, int position) {
        if (position < 1) {
            return Either.left(UseCaseErrors.validation("Round position must be at least 1"));
        }
        return hierarchyValidator.validateRound(season.getId(), position);
    }

    private Either<UseCaseError, RescheduleContext> validateReschedule(MatchContext matchCtx, int newRoundPosition) {
        return resolveTargetRound(matchCtx.context().season(), newRoundPosition)
                .flatMap(targetRound -> {
                    RescheduleContext ctx = new RescheduleContext(
                            matchCtx.context().season(),
                            matchCtx.context().currentRound(),
                            targetRound,
                            matchCtx.match());

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

    private Either<UseCaseError, RescheduleResult> performReschedule(RescheduleContext ctx, RescheduleMatchCommand cmd) {
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

    private record HierarchyContext(Season season, Round currentRound) {}

    private record MatchContext(HierarchyContext context, Match match) {}

    private record RescheduleContext(Season season, Round currentRound, Round targetRound, Match match) {}
}
