package com.ligitabl.api.usecases.team.updateteam;

import static com.ligitabl.api.shared.ValidationUtils.*;

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
        return requireUnique(
                teamRepo.isSlugInUseByAnotherTeam(team.getSlug(), team.getId()),
                team,
                UseCaseErrors.conflict(String.format("Team already exists for slug: %s.", team.getSlug())));
    }

    private Either<UseCaseError, Team> requireTeamExists(Team candidate) {
        // Only ensure existence, but keep passing through the caller's candidate
        // so that updated field values are not lost.
        return requireExists(
                teamRepo.existsById(candidate.getId()), candidate, UseCaseErrors.notFound("Team", candidate.getId()));
    }
}
