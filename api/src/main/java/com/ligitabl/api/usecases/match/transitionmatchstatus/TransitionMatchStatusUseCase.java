package com.ligitabl.api.usecases.match.transitionmatchstatus;

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
public class TransitionMatchStatusUseCase
        implements UseCase<TransitionMatchCommand, Either<UseCaseError, TransitionResult>> {

    private final MatchRepo matchRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final Clock clock;

    @Override
    @Transactional
    public Either<UseCaseError, TransitionResult> execute(TransitionMatchCommand cmd) {
        log.info("Executing TransitionMatchStatus: slug={}, status={}", cmd.getMatchSlug(), cmd.getNewStatus());

        return resolveHierarchy(cmd)
            .flatMap(context -> findMatch(context.round().getId(), cmd.getMatchSlug())
                .map(match -> new TransitionContext(context.round(), match, match.getStatus())))
            .flatMap(ctx -> validateAndTransition(ctx.match(), cmd)
                .map(match -> new TransitionContext(ctx.round(), match, ctx.oldStatus())))
                .flatMap(this::save);
    }

    private Either<UseCaseError, HierarchyContext> resolveHierarchy(TransitionMatchCommand cmd) {
        String competitionIdentifier = cmd.getCompetitionIdentifier().orElseGet(competitionDefaults::defaultCompetitionSlug);

        return hierarchyValidator.validateCompetition(competitionIdentifier)
                .flatMap(this::getActiveSeason)
                .flatMap(season -> resolveRound(season, cmd))
                .map(round -> new HierarchyContext(round));
    }

    private Either<UseCaseError, Season> getActiveSeason(Competition competition) {
        UUID activeSeasonId = competition.getActiveSeasonId();
        if (activeSeasonId == null) {
            return Either.left(UseCaseErrors.validation("Competition has no active season"));
        }

        return requireFound(seasonRepo.findById(activeSeasonId), UseCaseErrors.notFound("Season", activeSeasonId));
    }

    private Either<UseCaseError, Round> resolveRound(Season season, TransitionMatchCommand cmd) {
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

    private Either<UseCaseError, Match> validateAndTransition(Match match, TransitionMatchCommand cmd) {
        try {
            if (cmd.getNewStatus() == MatchStatus.FINISHED) {
                if (cmd.getScore() == null) {
                    return Either.left(UseCaseErrors.validation("Score is required when transitioning to FINISHED"));
                }
                match.setScore(cmd.getScore().getHomeGoals(), cmd.getScore().getAwayGoals());
            }

            match.transitionTo(cmd.getNewStatus(), cmd.getReason());
            return Either.right(match);

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
                    .roundPosition(ctx.round().getPosition())
                    .timestamp(now)
                    .build());

        } catch (Exception e) {
            return Either.left(UseCaseErrors.fromException(e));
        }
    }

    private record HierarchyContext(Round round) {}

    private record TransitionContext(Round round, Match match, MatchStatus oldStatus) {}
}
