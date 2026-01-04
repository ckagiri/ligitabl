package com.ligitabl.api.usecases.team.createteam;

import com.ligitabl.api.shared.UseCase;
import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.api.usecases.team.TeamMapper;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreateTeamUseCase implements UseCase<CreateTeamCommand, Either<UseCaseError, TeamDto>> {
    private final RequestValidator requestValidator;
    private final TeamCreationGuard teamCreationGuard;
    private final TeamMapper mapper;
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, TeamDto> execute(CreateTeamCommand command) {
        return requestValidator
                .validate(command)
                .flatMap(Either.catching(mapper::toEntity, UseCaseErrors::fromException))
                .flatMap(teamCreationGuard::validate)
                .map(teamRepo::create)
                .map(TeamDto::from);
    }
}
