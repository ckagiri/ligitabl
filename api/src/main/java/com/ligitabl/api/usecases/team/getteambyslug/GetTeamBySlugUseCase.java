package com.ligitabl.api.usecases.team.getteambyslug;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.shared.Either;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetTeamBySlugUseCase implements UseCase<GetTeamBySlugQuery, Either<UseCaseError, TeamDto>> {
    private final TeamRepo teamRepo;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, TeamDto> execute(GetTeamBySlugQuery query) {
        return requestValidator
                .validate(query)
                .map(GetTeamBySlugQuery::slug)
                .flatMap(slug -> requireFound(teamRepo.findBySlug(slug), UseCaseErrors.notFound("Team", "slug", slug)))
                .map(TeamDto::from);
    }
}
