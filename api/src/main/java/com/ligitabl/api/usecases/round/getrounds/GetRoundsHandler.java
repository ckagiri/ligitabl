package com.ligitabl.api.usecases.round.getrounds;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.round.RoundDto;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.RoundRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Service
@RequiredArgsConstructor
public class GetRoundsHandler implements GetRoundsUseCase {

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, List<RoundDto>> execute(GetRoundsQuery query) {
        return requestValidator
                .validate(query)
                .flatMap(q -> requireCompetitionExists(q.competitionSlug())
                        .flatMap(competition -> requireSeasonExists(competition.getId(), q.seasonSlug())))
                .map(Season::getId)
                .flatMap(Either.liftException(
                        roundRepo::findBySeasonId,
                        UseCaseErrors::fromException))
                .map(RoundDto::listOf);
    }

    private Either<UseCaseError, Competition> requireCompetitionExists(String competitionSlugStr) {
        return Either.fromException(
                        () -> CompetitionSlug.of(competitionSlugStr),
                        UseCaseErrors::fromException)
                .flatMap(this::findCompetitionBySlug);
    }

    private Either<UseCaseError, Competition> findCompetitionBySlug(CompetitionSlug competitionSlug) {
        return requireFound(
                competitionRepo.findBySlug(competitionSlug),
                UseCaseErrors.notFound("Competition", "slug", competitionSlug.value()));
    }

    private Either<UseCaseError, Season> requireSeasonExists(UUID competitionId, String seasonSlugStr) {
        return Either.fromException(
                        () -> SeasonSlug.of(seasonSlugStr),
                        UseCaseErrors::fromException)
                .flatMap(seasonSlug -> findSeasonByCompetitionAndSlug(competitionId, seasonSlug));
    }

    private Either<UseCaseError, Season> findSeasonByCompetitionAndSlug(UUID competitionId, SeasonSlug seasonSlug) {
        return requireFound(
                seasonRepo.findByCompetitionIdAndSlug(competitionId, seasonSlug),
                UseCaseErrors.notFound("Season", "slug", seasonSlug.value()));
    }
}
