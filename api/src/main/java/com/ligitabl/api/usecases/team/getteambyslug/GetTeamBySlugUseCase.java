package com.ligitabl.api.usecases.team.getteambyslug;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.team.TeamDto;

public interface GetTeamBySlugUseCase extends UseCase<GetTeamBySlugQuery, Either<UseCaseError, TeamDto>> {}
