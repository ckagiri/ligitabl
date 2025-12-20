package com.ligitabl.api.usecases.match.getdefaultroundmatches;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.match.MatchDto;
import com.ligitabl.api.usecases.match.MatchEnricher;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetDefaultRoundMatchesHandler implements GetDefaultRoundMatchesUseCase {

    private static final String DEFAULT_COMPETITION = "premier-league";

    private final HierarchyValidator hierarchyValidator;
    private final SeasonRepo seasonRepo;
    private final MatchRepo matchRepo;
    private final MatchEnricher matchEnricher;

    @Override
    public Either<UseCaseError, List<MatchDto>> execute(Void unused) {
        return hierarchyValidator
                .validateCompetition(DEFAULT_COMPETITION)
                .flatMap(this::getActiveSeason)
                .flatMap(this::getCurrentRoundId)
                .flatMap(Either.catching(matchRepo::findByRoundId, UseCaseErrors::fromException))
                .flatMap(matchEnricher::enrichWithTeams);
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
