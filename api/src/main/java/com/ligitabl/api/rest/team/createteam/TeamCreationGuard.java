package com.ligitabl.api.rest.team.createteam;

import static com.ligitabl.api.shared.ValidationUtils.*;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.Guard;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamCreationGuard implements Guard<Team> {
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, Team> validate(Team candidate) {
        return requireIdIsNull(candidate).flatMap(this::ensureSlugIsUnique);
    }

    private Either<UseCaseError, Team> ensureSlugIsUnique(Team candidate) {
        return requireUnique(
                teamRepo.existsBySlug(candidate.getSlug()),
                candidate,
                UseCaseErrors.conflict("Team with slug " + candidate.getSlug() + " already exists"));
    }
}
