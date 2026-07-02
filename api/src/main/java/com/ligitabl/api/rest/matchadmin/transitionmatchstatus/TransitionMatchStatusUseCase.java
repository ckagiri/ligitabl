package com.ligitabl.api.rest.matchadmin.transitionmatchstatus;

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
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransitionMatchStatusUseCase
        implements UseCase<TransitionMatchCommand, Either<UseCaseError, TransitionResult>> {

    private final MatchRepo matchRepo;
    private final RoundRepo roundRepo;
    private final StandingsRepo standingsRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final Clock clock;

    @Override
    @Transactional
    public Either<UseCaseError, TransitionResult> execute(TransitionMatchCommand cmd) {
        log.info("Executing TransitionMatchStatus: slug={}, status={}", cmd.getMatchSlug(), cmd.getNewStatus());

        String competitionIdentifier =
                cmd.getCompetitionIdentifier().orElseGet(competitionDefaults::defaultCompetitionSlug);

        return hierarchyValidator
                .resolveHierarchy(competitionIdentifier, cmd.getRoundPosition())
                .flatMap(context -> findMatch(context.round().getId(), cmd.getMatchSlug())
                        .map(match -> new MatchContext(context, match)))
                .flatMap(matchCtx -> validateAndTransition(matchCtx, cmd))
                .flatMap(this::save);
    }

    private Either<UseCaseError, Match> findMatch(UUID roundId, String matchSlug) {
        return requireFound(
                matchRepo.findByRoundIdAndSlug(roundId, matchSlug), UseCaseErrors.notFound("Match", "slug", matchSlug));
    }

    private Either<UseCaseError, TransitionContext> validateAndTransition(
            MatchContext matchCtx, TransitionMatchCommand cmd) {
        Match match = matchCtx.match();
        boolean isSetupMode = matchCtx.context().season().isInSetupMode();

        return hierarchyValidator
                .validateCurrentRound(matchCtx.context().season())
                .flatMap(currentRound -> {
                    try {
                        MatchStatus oldStatus = match.getStatus();

                        if (cmd.getNewStatus() == MatchStatus.FINISHED) {
                            if (cmd.getScore() == null) {
                                return Either.left(
                                        UseCaseErrors.validation("Score is required when transitioning to FINISHED"));
                            }
                            match.setScore(
                                    cmd.getScore().getHomeGoals(),
                                    cmd.getScore().getAwayGoals());
                        }

                        // Setup mode bypasses the normal match-day state machine.
                        match.transitionTo(cmd.getNewStatus(), cmd.getReason(), isSetupMode);
                        return Either.right(new TransitionContext(matchCtx.context(), match, oldStatus, currentRound));

                    } catch (IllegalStateException | IllegalArgumentException e) {
                        return Either.left(UseCaseErrors.validation(e.getMessage()));
                    } catch (Exception e) {
                        return Either.left(UseCaseErrors.fromException(e));
                    }
                });
    }

    private Either<UseCaseError, TransitionResult> save(TransitionContext ctx) {
        try {
            Match saved = matchRepo.save(ctx.match());
            markOutOfSyncIfPastRound(ctx);
            Instant now = clock.instant();

            return Either.right(TransitionResult.builder()
                    .matchId(saved.getId())
                    .matchSlug(saved.getSlug())
                    .oldStatus(ctx.oldStatus())
                    .newStatus(saved.getStatus())
                    .roundPosition(ctx.context().round().getPosition())
                    .timestamp(now)
                    .build());

        } catch (Exception e) {
            return Either.left(UseCaseErrors.fromException(e));
        }
    }

    // Only fires in setup mode — A status correction on a match already in a past round means that round's (and
    // everything
    // since) finalized standings are now stale.
    private void markOutOfSyncIfPastRound(TransitionContext ctx) {
        if (!ctx.context().season().isInSetupMode()) {
            return;
        }

        int matchRoundPosition = ctx.context().round().getPosition();
        int currentPosition = ctx.seasonCurrentRound().getPosition();
        if (matchRoundPosition >= currentPosition) {
            return;
        }

        UUID seasonId = ctx.context().season().getId();
        roundRepo.markUnfinalizedBetween(seasonId, matchRoundPosition, currentPosition);
        standingsRepo.markUnfinalisedBetween(seasonId, matchRoundPosition, currentPosition);
        log.info(
                "Transition cascade: marked rounds {}-{} unfinalized for season {} (match {} status changed)",
                matchRoundPosition,
                currentPosition,
                seasonId,
                ctx.match().getSlug());
    }

    private record MatchContext(HierarchyContext context, Match match) {}

    private record TransitionContext(
            HierarchyContext context, Match match, MatchStatus oldStatus, Round seasonCurrentRound) {}
}
