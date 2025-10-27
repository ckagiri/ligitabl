package com.ligitabl.api.usecases.team;

import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.AbstractGuard;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.shared.Either;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamGuard extends AbstractGuard<Team> {
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, Team> forCreate(Team candidate) {
        return ensureIdIsNull(candidate, candidate::getId).flatMap(this::ensureSlugIsUnique);
    }

    @Override
    public Either<UseCaseError, Team> forUpdate(Team candidate) {
        return ensureIdIsNotNull(candidate, candidate::getId).flatMap(this::requireTeamExists)
            .flatMap(this::ensureSlugIsUniqueForUpdate);
    }

    private Either<UseCaseError, Team> ensureSlugIsUnique(Team team) {
        if (teamRepo.existsBySlug(team.getSlug())) {
            return Either.left(UseCaseErrors.validation("Team with slug '" + team.getSlug() + "' already exists"));
        }
        return Either.right(team);
    }

    private Either<UseCaseError, Team> ensureSlugIsUniqueForUpdate(Team team) {
        return teamRepo.findBySlug(team.getSlug()).filter(existing -> !existing.getId().equals(team.getId()))
            .<Either<UseCaseError, Team>>map(conflict -> Either
                .left(UseCaseErrors.conflict("Slug '" + team.getSlug() + "' is already used by another team")))
            .orElse(Either.right(team));
    }

    private Either<UseCaseError, Team> requireTeamExists(Team team) {
        // Only ensure existence, but keep passing through the caller's candidate
        // so that updated field values are not lost.
        return teamRepo.findById(team.getId()).<Either<UseCaseError, Team>>map(found -> Either.right(team))
            .orElse(Either.left(UseCaseErrors.notFound("Team", team.getId())));
    }
}
