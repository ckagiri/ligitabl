package com.ligitabl.api.rest.team.getteambyid;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.rest.team.TeamDto;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetTeamByIdUseCase implements UseCase<GetTeamByIdQuery, Either<UseCaseError, TeamDto>> {
    private final RequestValidator requestValidator;
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, TeamDto> execute(GetTeamByIdQuery query) {
        return requestValidator
                .validate(query)
                .map(GetTeamByIdQuery::getUuid)
                .flatMap(id -> requireFound(teamRepo.findById(id), UseCaseErrors.notFound("Team", id)))
                .map(TeamDto::from);
    }
}
