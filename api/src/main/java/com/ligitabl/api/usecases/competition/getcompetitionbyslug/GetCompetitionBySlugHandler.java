package com.ligitabl.api.usecases.competition.getcompetitionbyslug;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.competition.CompetitionDto;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.repo.CompetitionRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCompetitionBySlugHandler implements GetCompetitionBySlugUseCase {

    private final CompetitionRepo competitionRepo;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, CompetitionDto> execute(GetCompetitionBySlugQuery query) {
        return requestValidator
                .validate(query)
                .map(GetCompetitionBySlugQuery::slug)
                .flatMap(Either.catching(CompetitionSlug::of, UseCaseErrors::fromException))
                .flatMap(slug -> requireFound(
                        competitionRepo.findBySlug(slug), UseCaseErrors.notFound("Competition", "slug", slug.value())))
                .map(CompetitionDto::from);
    }
}
