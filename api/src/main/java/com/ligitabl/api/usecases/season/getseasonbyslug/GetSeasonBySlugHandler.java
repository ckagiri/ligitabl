package com.ligitabl.api.usecases.season.getseasonbyslug;

import com.ligitabl.api.usecases.shared.HierarchyValidator;
import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.season.SeasonDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeasonBySlugHandler implements GetSeasonBySlugUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, SeasonDto> execute(GetSeasonBySlugQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> hierarchyValidator.validateCompetitionAndSeason(
                        q.competitionSlug(), q.seasonSlug()))
                .map(SeasonDto::from);
    }
}
