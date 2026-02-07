package com.ligitabl.api.rest.season.getseasonbyslug;

import org.springframework.stereotype.Service;

import com.ligitabl.api.rest.season.SeasonDto;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeasonBySlugUseCase implements UseCase<GetSeasonBySlugQuery, Either<UseCaseError, SeasonDto>> {

    private final HierarchyValidator hierarchyValidator;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, SeasonDto> execute(GetSeasonBySlugQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> hierarchyValidator.validateCompetitionAndSeason(q.competitionSlug(), q.seasonSlug()))
                .map(SeasonDto::from);
    }
}
