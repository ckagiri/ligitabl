package com.ligitabl.api.usecases.team.updateteam;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.model.shared.Either;

public interface IUpdateTeamUseCase extends UseCase<UpdateTeamCommand, Either<UseCaseError, TeamDto>> {}
