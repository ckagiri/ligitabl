package com.ligitabl.api.usecases.season.getseasons;

import java.util.List;

import com.ligitabl.api.shared.UseCase;
import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.season.SeasonDto;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeasonsUseCase implements UseCase<GetSeasonsQuery, Either<UseCaseError, List<SeasonDto>>> {

    private final HierarchyValidator hierarchyValidator;
    private final SeasonRepo seasonRepo;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, List<SeasonDto>> execute(GetSeasonsQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> hierarchyValidator.validateCompetition(q.competitionSlug()))
                .map(Competition::getId)
                .flatMap(Either.catching(seasonRepo::findAllByCompetitionId, UseCaseErrors::fromException))
                .map(SeasonDto::listOf);
    }
}
