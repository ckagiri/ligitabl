package com.ligitabl.api.usecases.team.deleteteam;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.Unit;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;

public interface DeleteTeamUseCase extends UseCase<DeleteTeamCommand, Either<UseCaseError, Unit>> {}
