package com.ligitabl.api.usecases.team.updateteam;

import static com.ligitabl.api.shared.ValidationUtils.requireIdIsNotNull;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.Guard;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.shared.Either;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamUpdateGuard implements Guard<Team> {
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, Team> validate(Team candidate) {
        return requireIdIsNotNull(candidate)
                .flatMap(this::requireTeamExists)
                .flatMap(this::ensureSlugIsUniqueForUpdate);
    }

    private Either<UseCaseError, Team> ensureSlugIsUniqueForUpdate(Team team) {
        if (teamRepo.isSlugInUseByAnotherTeam(team.getSlug(), team.getId())) {
            return Either.left(UseCaseErrors.conflict("Slug '" + team.getSlug() + "' is already used by another team"));
        }
        return Either.right(team);
    }

    private Either<UseCaseError, Team> requireTeamExists(Team candidate) {
        // Only ensure existence, but keep passing through the caller's candidate
        // so that updated field values are not lost.
        return teamRepo.findById(candidate.getId())
                .<Either<UseCaseError, Team>>map(_found -> Either.right(candidate))
                .orElse(Either.left(UseCaseErrors.notFound("Team", candidate.getId())));
    }
}
