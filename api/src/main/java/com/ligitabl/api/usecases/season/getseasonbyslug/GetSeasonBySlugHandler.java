package com.ligitabl.api.usecases.season.getseasonbyslug;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.season.SeasonDto;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
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
                        .flatMap(competition -> requireSeasonExists(competition.getId(), q.seasonSlug())))
                .map(SeasonDto::from);
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

    private Either<UseCaseError, Season> requireSeasonExists(UUID competitionId, String seasonSlugStr) {
        return Either.catching(() -> SeasonSlug.of(seasonSlugStr), UseCaseErrors::fromException)
                .flatMap(seasonSlug -> findSeasonByCompetitionAndSlug(competitionId, seasonSlug));
    }

    private Either<UseCaseError, Season> findSeasonByCompetitionAndSlug(UUID competitionId, SeasonSlug seasonSlug) {
        return requireFound(
                seasonRepo.findByCompetitionIdAndSlug(competitionId, seasonSlug),
                UseCaseErrors.notFound("Season", "slug", seasonSlug.value()));
    }
}
