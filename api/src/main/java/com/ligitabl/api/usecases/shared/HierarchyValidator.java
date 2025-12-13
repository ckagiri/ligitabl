package com.ligitabl.api.usecases.shared;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

@Service
@RequiredArgsConstructor
public class HierarchyValidator {

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;

    public Either<UseCaseError, Competition> validateCompetition(String competitionSlugStr) {
        return Either.catching(
                        () -> CompetitionSlug.of(competitionSlugStr),
                        UseCaseErrors::fromException)
                .flatMap(this::findCompetitionBySlug);
    }

    public Either<UseCaseError, Season> validateSeason(UUID competitionId, String seasonSlugStr) {
        return Either.catching(
                        () -> SeasonSlug.of(seasonSlugStr),
                        UseCaseErrors::fromException)
                .flatMap(seasonSlug -> findSeasonByCompetitionAndSlug(competitionId, seasonSlug));
    }

    public Either<UseCaseError, Round> validateRound(UUID seasonId, int position) {
        return requireFound(
                roundRepo.findBySeasonIdAndPosition(seasonId, position),
                UseCaseErrors.notFound("Round", "position", String.valueOf(position)));
    }

    public Either<UseCaseError, Season> validateCompetitionAndSeason(
            String competitionSlugStr, String seasonSlugStr) {
        return validateCompetition(competitionSlugStr)
                .flatMap(competition -> validateSeason(competition.getId(), seasonSlugStr));
    }

    public Either<UseCaseError, Round> validateCompetitionSeasonAndRound(
            String competitionSlugStr, String seasonSlugStr, int position) {
        return validateCompetitionAndSeason(competitionSlugStr, seasonSlugStr)
                .flatMap(season -> validateRound(season.getId(), position));
    }

    private Either<UseCaseError, Competition> findCompetitionBySlug(CompetitionSlug competitionSlug) {
        return requireFound(
                competitionRepo.findBySlug(competitionSlug),
                UseCaseErrors.notFound("Competition", "slug", competitionSlug.value()));
    }

    private Either<UseCaseError, Season> findSeasonByCompetitionAndSlug(UUID competitionId, SeasonSlug seasonSlug) {
        return requireFound(
                seasonRepo.findByCompetitionIdAndSlug(competitionId, seasonSlug),
                UseCaseErrors.notFound("Season", "slug", seasonSlug.value()));
    }
}

