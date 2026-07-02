package com.ligitabl.api.rest.match.getdefaultroundmatches;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.match.MatchEnricher;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Round;
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

    @Override
    public Either<UseCaseError, RoundMatchesResult> execute(GetDefaultRoundMatchesQuery query) {
        String competitionIdentifier = getEffectiveCompetitionIdentifier(query);

        return hierarchyValidator
                .resolveHierarchy(competitionIdentifier, query.getRoundPosition())
                .flatMap(ctx -> resolveCurrentRound(ctx.season()).flatMap(currentRound -> fetchMatches(ctx.round())
                        .flatMap(matchEnricher::enrichWithTeams)
                        .map(matches -> new RoundMatchesResult(
                                ctx.season().getId(),
                                ctx.season().getSlug().value(),
                                ctx.round().getPosition(),
                                currentRound.getPosition(),
                                ctx.season().getMaxRounds(),
                                matches,
                                ctx.round().isFinalized(),
                                ctx.season().isInSetupMode(),
                                isStandingsFinalised(
                                        ctx.season().getId(), ctx.round().getPosition())))));
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
