package com.ligitabl.api.rest.competition.getcompetitionbyslug;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.rest.competition.CompetitionDto;
import com.ligitabl.api.rest.shared.HierarchyValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCompetitionBySlugUseCase
        implements UseCase<GetCompetitionBySlugQuery, Either<UseCaseError, CompetitionDto>> {

    private final HierarchyValidator hierarchyValidator;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, CompetitionDto> execute(GetCompetitionBySlugQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> hierarchyValidator.validateCompetition(q.slug()))
                .map(CompetitionDto::from);
    }
}
