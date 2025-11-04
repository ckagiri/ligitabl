package com.ligitabl.api.usecases.team.updateteam;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.team.TeamDto;

public interface UpdateTeamUseCase extends UseCase<UpdateTeamCommand, Either<UseCaseError, TeamDto>> {}
