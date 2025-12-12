package com.ligitabl.api.usecases.season.getseasons;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.season.SeasonDto;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSeasonsHandler implements GetSeasonsUseCase {

    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, List<SeasonDto>> execute(GetSeasonsQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> requireCompetitionExists(q.competitionSlug()))
                .map(Competition::getId)
                .flatMap(Either.catching(seasonRepo::findAllByCompetitionId, UseCaseErrors::fromException))
                .map(SeasonDto::listOf);
    }

    private Either<UseCaseError, Competition> requireCompetitionExists(String competitionSlugStr) {
        return Either.catching(() -> CompetitionSlug.of(competitionSlugStr), UseCaseErrors::fromException)
                .flatMap(this::findCompetitionBySlug);
    }

    private Either<UseCaseError, Competition> findCompetitionBySlug(CompetitionSlug competitionSlug) {
        return requireFound(
                competitionRepo.findBySlug(competitionSlug),
                UseCaseErrors.notFound("Competition", "slug", competitionSlug.value()));
    }
}
