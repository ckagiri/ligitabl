package com.ligitabl.api.usecases.round.getrounds;

import java.util.List;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.round.RoundDto;

public interface GetRoundsUseCase
        extends UseCase<GetRoundsQuery, Either<UseCaseError, List<RoundDto>>> {}
