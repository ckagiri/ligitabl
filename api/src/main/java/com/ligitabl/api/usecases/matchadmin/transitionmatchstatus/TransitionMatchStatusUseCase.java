package com.ligitabl.api.usecases.matchadmin.transitionmatchstatus;

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
import com.ligitabl.api.usecases.shared.HierarchyValidator.HierarchyContext;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransitionMatchStatusUseCase
        implements UseCase<TransitionMatchCommand, Either<UseCaseError, TransitionResult>> {

    private final MatchRepo matchRepo;
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

        try {
            MatchStatus oldStatus = match.getStatus();

            if (cmd.getNewStatus() == MatchStatus.FINISHED) {
                if (cmd.getScore() == null) {
                    return Either.left(UseCaseErrors.validation("Score is required when transitioning to FINISHED"));
                }
                match.setScore(cmd.getScore().getHomeGoals(), cmd.getScore().getAwayGoals());
            }

            match.transitionTo(cmd.getNewStatus(), cmd.getReason());
            return Either.right(new TransitionContext(matchCtx.context(), match, oldStatus));

        } catch (IllegalStateException | IllegalArgumentException e) {
            return Either.left(UseCaseErrors.validation(e.getMessage()));
        } catch (Exception e) {
            return Either.left(UseCaseErrors.fromException(e));
        }
    }

    private Either<UseCaseError, TransitionResult> save(TransitionContext ctx) {
        try {
            Match saved = matchRepo.save(ctx.match());
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

    private record MatchContext(HierarchyContext context, Match match) {}

    private record TransitionContext(HierarchyContext context, Match match, MatchStatus oldStatus) {}
}
