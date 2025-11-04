package com.ligitabl.api.usecases.team.getteambyid;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.team.TeamDto;

public interface GetTeamByIdUseCase extends UseCase<GetTeamByIdQuery, Either<UseCaseError, TeamDto>> {}
