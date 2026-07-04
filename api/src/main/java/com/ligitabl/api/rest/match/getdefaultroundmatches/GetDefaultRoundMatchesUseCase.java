package com.ligitabl.api.rest.match.getdefaultroundmatches;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.match.MatchDto;
import com.ligitabl.api.rest.match.MatchEnricher;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.rest.shared.HierarchyValidator.HierarchyContext;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetDefaultRoundMatchesUseCase
        implements UseCase<GetDefaultRoundMatchesQuery, Either<UseCaseError, RoundMatchesResult>> {
    private final MatchRepo matchRepo;
    private final MatchEnricher matchEnricher;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final RoundRepo roundRepo;
    private final StandingsRepo standingsRepo;

    private record RoundContext(HierarchyContext ctx, Round currentRound) {}

    private record MatchesContext(RoundContext roundContext, List<Match> rawMatches, List<MatchDto> matches) {}

    @Override
    public Either<UseCaseError, RoundMatchesResult> execute(GetDefaultRoundMatchesQuery query) {
        String competitionIdentifier = getEffectiveCompetitionIdentifier(query);

        return hierarchyValidator
                .resolveHierarchy(competitionIdentifier, query.getRoundPosition())
                .flatMap(this::withCurrentRound)
                .flatMap(this::withMatches)
                .map(this::toResult);
    }

    private Either<UseCaseError, RoundContext> withCurrentRound(HierarchyContext ctx) {
        return resolveCurrentRound(ctx.season()).map(currentRound -> new RoundContext(ctx, currentRound));
    }

    private Either<UseCaseError, MatchesContext> withMatches(RoundContext roundContext) {
        return fetchMatches(roundContext.ctx().round()).flatMap(rawMatches -> matchEnricher
                .enrichWithTeams(rawMatches)
                .map(matches -> new MatchesContext(roundContext, rawMatches, matches)));
    }

    private RoundMatchesResult toResult(MatchesContext matchesContext) {
        HierarchyContext ctx = matchesContext.roundContext().ctx();
        Round currentRound = matchesContext.roundContext().currentRound();
        List<Match> rawMatches = matchesContext.rawMatches();

        return RoundMatchesResult.builder()
                .seasonId(ctx.season().getId())
                .seasonSlug(ctx.season().getSlug().value())
                .viewingRound(ctx.round().getPosition())
                .currentRound(currentRound.getPosition())
                .lastRound(ctx.season().getMaxRounds())
                .matches(matchesContext.matches())
                .roundFinalized(ctx.round().isFinalized())
                .roundAdvanced(currentRound.isAdvanced())
                .autoAdvanceScheduled(currentRound.getAdvanceAt() != null)
                .seasonInSetupMode(ctx.season().isInSetupMode())
                .seasonCompleted(ctx.season().isCompleted())
                .standingsFinalised(
                        isStandingsFinalised(ctx.season().getId(), ctx.round().getPosition()))
                .allMatchesTerminalOrBlocking(rawMatches.stream().allMatch(m -> m.isComplete() || m.isBlocking()))
                .allMatchesFinished(
                        !rawMatches.isEmpty() && rawMatches.stream().allMatch(m -> m.getStatus() == MatchStatus.FINISHED))
                .matchesComplete(Round.computeMatchStatus(rawMatches) == RoundStatus.COMPLETED)
                .build();
    }

    private boolean isStandingsFinalised(UUID seasonId, int roundPosition) {
        return standingsRepo
                .findBySeasonAndRoundPosition(seasonId, roundPosition)
                .map(Standings::isFinalised)
                .orElse(false);
    }

    private Either<UseCaseError, List<Match>> fetchMatches(Round round) {
        return Either.catching(() -> matchRepo.findByRoundId(round.getId()), UseCaseErrors::fromException);
    }

    private Either<UseCaseError, Round> resolveCurrentRound(Season season) {
        if (season.getCurrentRoundId() == null) {
            return Either.left(UseCaseErrors.validation("Season has no current round"));
        }

        return requireFound(
                roundRepo.findById(season.getCurrentRoundId()),
                UseCaseErrors.notFound("Round", season.getCurrentRoundId()));
    }

    private String getEffectiveCompetitionIdentifier(GetDefaultRoundMatchesQuery query) {
        return query.getCompetitionIdentifier().orElseGet(competitionDefaults::defaultCompetitionSlug);
    }
}
