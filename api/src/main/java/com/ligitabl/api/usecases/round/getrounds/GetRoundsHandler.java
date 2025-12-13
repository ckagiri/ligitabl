package com.ligitabl.api.usecases.round.getrounds;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.round.RoundDto;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetRoundsHandler implements GetRoundsUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final RoundRepo roundRepo;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, List<RoundDto>> execute(GetRoundsQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> hierarchyValidator.validateCompetitionAndSeason(q.competitionSlug(), q.seasonSlug()))
                .map(Season::getId)
                .flatMap(Either.catching(roundRepo::findBySeasonId, UseCaseErrors::fromException))
                .map(RoundDto::listOf);
    }
}
