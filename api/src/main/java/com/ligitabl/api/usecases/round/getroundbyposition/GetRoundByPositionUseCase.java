package com.ligitabl.api.usecases.round.getroundbyposition;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.round.RoundDto;

public interface GetRoundByPositionUseCase
        extends UseCase<GetRoundByPositionQuery, Either<UseCaseError, RoundDto>> {}
