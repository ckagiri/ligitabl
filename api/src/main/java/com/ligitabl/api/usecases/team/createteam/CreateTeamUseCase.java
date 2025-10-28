package com.ligitabl.api.usecases.team.createteam;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.api.usecases.team.TeamGuard;
import com.ligitabl.api.usecases.team.TeamMapper;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.shared.Either;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreateTeamUseCase implements UseCase<CreateTeamCommand, Either<UseCaseError, TeamDto>> {
    private final RequestValidator requestValidator;
    private final TeamGuard teamGuard;
    private final TeamMapper mapper;
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, TeamDto> execute(CreateTeamCommand command) {
        return requestValidator
                .validate(command)
                .map(mapper::toEntity)
                .flatMap(teamGuard::forCreate)
                .map(teamRepo::create)
                .map(TeamDto::from);
    }
}
