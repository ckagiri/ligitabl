package com.ligitabl.api.rest.shared;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HierarchyValidator {

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;

    public record HierarchyContext(Season season, Round round) {}

    public Either<UseCaseError, HierarchyContext> resolveHierarchy(
            String competitionIdentifier, Integer roundPosition) {
        return validateCompetition(competitionIdentifier)
                .flatMap(this::validateActiveSeason)
                .flatMap(season -> {
                    if (roundPosition == null) {
                        return validateCurrentRound(season).map(round -> new HierarchyContext(season, round));
                    }

                    if (roundPosition < 1) {
                        return Either.left(UseCaseErrors.validation("Round position must be at least 1"));
                    }

                    return validateRound(season.getId(), roundPosition)
                            .map(round -> new HierarchyContext(season, round));
                });
    }

    public Either<UseCaseError, Competition> validateCompetition(String slugOrCode) {
        return requireFound(
                competitionRepo.findBySlugOrCode(slugOrCode), UseCaseErrors.notFound("Competition", slugOrCode));
    }

    public Either<UseCaseError, Season> validateActiveSeason(Competition competition) {
        if (competition == null) {
            return Either.left(UseCaseErrors.validation("Competition must not be null"));
        }

        UUID activeSeasonId = competition.getActiveSeasonId();
        if (activeSeasonId == null) {
            return Either.left(UseCaseErrors.validation("Competition has no active season"));
        }

        return requireFound(seasonRepo.findById(activeSeasonId), UseCaseErrors.notFound("Season", activeSeasonId));
    }

    public Either<UseCaseError, Round> validateCurrentRound(Season season) {
        if (season == null) {
            return Either.left(UseCaseErrors.validation("Season must not be null"));
        }

        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            return Either.left(UseCaseErrors.validation("Season has no current round"));
        }

        return requireFound(roundRepo.findById(currentRoundId), UseCaseErrors.notFound("Round", currentRoundId));
    }

    public Either<UseCaseError, Season> validateSeason(UUID competitionId, String seasonSlugStr) {
        return Either.catching(() -> SeasonSlug.of(seasonSlugStr), UseCaseErrors::fromException)
                .flatMap(seasonSlug -> findSeasonByCompetitionAndSlug(competitionId, seasonSlug));
    }

    public Either<UseCaseError, Round> validateRound(UUID seasonId, int position) {
        return requireFound(
                roundRepo.findBySeasonIdAndPosition(seasonId, position),
                UseCaseErrors.notFound("Round", "position", String.valueOf(position)));
    }

    public Either<UseCaseError, Season> validateCompetitionAndSeason(String competitionSlugStr, String seasonSlugStr) {
        return validateCompetition(competitionSlugStr)
                .flatMap(competition -> validateSeason(competition.getId(), seasonSlugStr));
    }

    public Either<UseCaseError, Round> validateCompetitionSeasonAndRound(
            String competitionSlugStr, String seasonSlugStr, int position) {
        return validateCompetitionAndSeason(competitionSlugStr, seasonSlugStr)
                .flatMap(season -> validateRound(season.getId(), position));
    }

    private Either<UseCaseError, Season> findSeasonByCompetitionAndSlug(UUID competitionId, SeasonSlug seasonSlug) {
        return requireFound(
                seasonRepo.findByCompetitionIdAndSlug(competitionId, seasonSlug),
                UseCaseErrors.notFound("Season", "slug", seasonSlug.value()));
    }
}
