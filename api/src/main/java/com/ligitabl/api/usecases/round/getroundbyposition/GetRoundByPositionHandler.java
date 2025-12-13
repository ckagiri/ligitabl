package com.ligitabl.api.usecases.round.getroundbyposition;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.round.RoundDto;
import com.ligitabl.api.usecases.shared.HierarchyValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetRoundByPositionHandler implements GetRoundByPositionUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, RoundDto> execute(GetRoundByPositionQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> hierarchyValidator.validateCompetitionSeasonAndRound(
                        q.competitionSlug(), q.seasonSlug(), q.position()))
                .map(RoundDto::from);
    }
}
