package com.ligitabl.api.rest.match.getdefaultroundmatches;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;

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
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;

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

    @Override
    public Either<UseCaseError, RoundMatchesResult> execute(GetDefaultRoundMatchesQuery query) {
        String competitionIdentifier = getEffectiveCompetitionIdentifier(query);

        return hierarchyValidator
                .resolveHierarchy(competitionIdentifier, query.getRoundPosition())
                .flatMap(ctx -> resolveCurrentRound(ctx.season()).flatMap(currentRound -> fetchMatches(ctx.round())
                        .flatMap(matchEnricher::enrichWithTeams)
                        .map(matches -> new RoundMatchesResult(
                                ctx.season().getId(),
                                ctx.round().getPosition(),
                                currentRound.getPosition(),
                                ctx.season().getMaxRounds(),
                                matches))));
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
