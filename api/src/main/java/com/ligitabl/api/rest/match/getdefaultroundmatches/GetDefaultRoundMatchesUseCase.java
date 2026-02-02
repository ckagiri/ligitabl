package com.ligitabl.api.rest.match.getdefaultroundmatches;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.rest.match.MatchDto;
import com.ligitabl.api.rest.match.MatchEnricher;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetDefaultRoundMatchesUseCase
        implements UseCase<GetDefaultRoundMatchesQuery, Either<UseCaseError, List<MatchDto>>> {
    private final MatchRepo matchRepo;
    private final MatchEnricher matchEnricher;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;

    @Override
    public Either<UseCaseError, List<MatchDto>> execute(GetDefaultRoundMatchesQuery query) {
        String competitionIdentifier = getEffectiveCompetitionIdentifier(query);

        return hierarchyValidator
                .resolveHierarchy(competitionIdentifier, query.getRoundPosition())
                .flatMap(ctx -> Either.catching(
                        () -> matchRepo.findByRoundId(ctx.round().getId()), UseCaseErrors::fromException))
                .flatMap(matchEnricher::enrichWithTeams);
    }

    private String getEffectiveCompetitionIdentifier(GetDefaultRoundMatchesQuery query) {
        return query.getCompetitionIdentifier().orElseGet(competitionDefaults::defaultCompetitionSlug);
    }
}
