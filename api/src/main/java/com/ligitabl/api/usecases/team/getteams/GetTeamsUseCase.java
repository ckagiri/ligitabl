package com.ligitabl.api.usecases.team.getteams;

import java.util.List;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.team.TeamDto;

public interface GetTeamsUseCase extends UseCase<Void, Either<UseCaseError, List<TeamDto>>> {

}
