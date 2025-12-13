package com.ligitabl.api.usecases.competition.getcompetitionbyslug;

import com.ligitabl.api.usecases.shared.HierarchyValidator;
import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.competition.CompetitionDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCompetitionBySlugHandler implements GetCompetitionBySlugUseCase {

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

