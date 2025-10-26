package com.ligitabl.api.usecases.team.getteambyslug;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validator.RequestValidator;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.shared.Either;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

@Service
@RequiredArgsConstructor
public class GetTeamBySlugUseCase implements UseCase<GetTeamBySlugQuery, Either<UseCaseError, Team>> {
    private final TeamRepo teamRepo;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, Team> execute(GetTeamBySlugQuery query) {
        return requestValidator.validate(query)
                .map(GetTeamBySlugQuery::slug)
                .flatMap(slug -> requireFound(
                        teamRepo.findBySlug(slug),
                        new NotFoundError("Team", "slug", slug)));
    }
}
