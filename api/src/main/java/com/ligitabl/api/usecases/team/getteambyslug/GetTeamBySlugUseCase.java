package com.ligitabl.api.usecases.team.getteambyslug;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.shared.Either;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

@Service
@RequiredArgsConstructor
public class GetTeamBySlugUseCase implements UseCase<GetTeamBySlugQuery, Either<UseCaseError, Team>> {
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, Team> execute(GetTeamBySlugQuery query) {
        return TeamSlug.of(query.slug())
         .mapLeft(error -> (UseCaseError) new ValidationError(error.message()))
         .flatMap(slug -> requireFound(
             teamRepo.findBySlug(slug.value()),
             new NotFoundError("Team", "slug", slug.value())
         ));
    }
}
