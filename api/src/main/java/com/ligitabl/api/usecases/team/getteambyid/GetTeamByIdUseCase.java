package com.ligitabl.api.usecases.team.getteambyid;

import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.model.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.model.repo.TeamRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

@Service
@RequiredArgsConstructor
public class GetTeamByIdUseCase implements UseCase<GetTeamByIdQuery, Either<UseCaseError, TeamDto>> {
    private final RequestValidator requestValidator;
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, TeamDto> execute(GetTeamByIdQuery query) {
        return requestValidator.validate(query)
            .map(GetTeamByIdQuery::getUuid)
            .flatMap(id -> requireFound(
                teamRepo.findById(id),
                new NotFoundError("Team", id)))
            .map(TeamDto::from);
    }
}
