package com.ligitabl.api.usecases.match.getroundmatches;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.match.MatchDto;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.api.usecases.shared.MatchEnricher;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetRoundMatchesHandler implements GetRoundMatchesUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final MatchRepo matchRepo;
    private final MatchEnricher matchEnricher;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, List<MatchDto>> execute(GetRoundMatchesQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> hierarchyValidator.validateCompetitionSeasonAndRound(
                        q.competitionSlug(), q.seasonSlug(), q.position()))
                .map(Round::getId)
                .flatMap(Either.catching(matchRepo::findByRoundId, UseCaseErrors::fromException))
                .flatMap(matchEnricher::enrichWithTeams);
    }
}
