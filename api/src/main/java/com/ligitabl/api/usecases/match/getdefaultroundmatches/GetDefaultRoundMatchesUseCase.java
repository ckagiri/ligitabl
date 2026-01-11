package com.ligitabl.api.usecases.match.getdefaultroundmatches;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;
import java.util.UUID;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.repo.CompetitionRepo;
import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.match.MatchDto;
import com.ligitabl.api.usecases.match.MatchEnricher;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetDefaultRoundMatchesUseCase implements UseCase<GetDefaultRoundMatchesQuery, Either<UseCaseError, List<MatchDto>>> {
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final MatchRepo matchRepo;
    private final MatchEnricher matchEnricher;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;

    @Override
    public Either<UseCaseError, List<MatchDto>> execute(GetDefaultRoundMatchesQuery query) {
        String competitionIdentifier = getEffectiveCompetitionIdentifier(query);

        return hierarchyValidator
                .validateCompetition(competitionIdentifier)
                .flatMap(this::getActiveSeason)
                .flatMap(this::getCurrentRoundId)
                .flatMap(Either.catching(matchRepo::findByRoundId, UseCaseErrors::fromException))
                .flatMap(matchEnricher::enrichWithTeams);
    }

    private String getEffectiveCompetitionIdentifier(GetDefaultRoundMatchesQuery query) {
        return query.getCompetitionIdentifier()
                .orElseGet(competitionDefaults::defaultCompetitionSlug);
    }

    private Either<UseCaseError, Season> getActiveSeason(Competition competition) {
        UUID activeSeasonId = competition.getActiveSeasonId();
        if (activeSeasonId == null) {
            return Either.left(UseCaseErrors.validation("Competition has no active season"));
        }

        return requireFound(seasonRepo.findById(activeSeasonId), UseCaseErrors.notFound("Season", activeSeasonId));
    }

    private Either<UseCaseError, UUID> getCurrentRoundId(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            return Either.left(UseCaseErrors.validation("Season has no current round"));
        }
        return Either.right(currentRoundId);
    }
}
