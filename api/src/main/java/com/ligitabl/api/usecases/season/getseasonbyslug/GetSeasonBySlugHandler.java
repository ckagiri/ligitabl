package com.ligitabl.api.usecases.season.getseasonbyslug;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.season.SeasonDto;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeasonBySlugHandler implements GetSeasonBySlugUseCase {

    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, SeasonDto> execute(GetSeasonBySlugQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> requireCompetitionExists(q.competitionSlug())
                        .map(_competition -> q))
                .map(GetSeasonBySlugQuery::seasonSlug)
                .flatMap(Either.liftException(
                        SeasonSlug::of,
                        UseCaseErrors::fromException
                ))
                .flatMap(this::findSeasonBySlug)
                .map(SeasonDto::from);
    }

    private Either<UseCaseError, Competition> requireCompetitionExists(String competitionSlugStr) {
        return Either.fromException(
                        () -> CompetitionSlug.of(competitionSlugStr),
                        UseCaseErrors::fromException
                )
                .flatMap(this::findCompetitionBySlug);
    }

    private Either<UseCaseError, Competition> findCompetitionBySlug(CompetitionSlug competitionSlug) {
        return requireFound(
                competitionRepo.findBySlug(competitionSlug),
                UseCaseErrors.notFound("Competition", "slug", competitionSlug.value())
        );
    }

    private Either<UseCaseError, Season> findSeasonBySlug(SeasonSlug seasonSlug) {
        return requireFound(
                seasonRepo.findBySlug(seasonSlug),
                UseCaseErrors.notFound("Season", "slug", seasonSlug.value())
        );
    }
}
