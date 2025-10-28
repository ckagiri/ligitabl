package com.ligitabl.api.usecases.team.createteam;

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
public class TeamCreationGuard implements Guard<Team> {
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, Team> validate(Team candidate) {
        return requireIdIsNull(candidate).flatMap(this::ensureSlugIsUnique);
    }

    private Either<UseCaseError, Team> ensureSlugIsUnique(Team team) {
        return requireNot(
                teamRepo.isSlugInUseByAnotherTeam(team.getSlug(), team.getId()),
                UseCaseErrors.validation("slug", "Slug already exists"),
                team);
    }
}
