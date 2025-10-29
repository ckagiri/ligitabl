package com.ligitabl.api.usecases.team.createteam;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.model.shared.Either;

public interface ICreateTeamUseCase extends UseCase<CreateTeamCommand, Either<UseCaseError, TeamDto>> {}
