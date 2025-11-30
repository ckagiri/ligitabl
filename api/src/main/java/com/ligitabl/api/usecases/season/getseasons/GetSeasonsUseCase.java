package com.ligitabl.api.usecases.season.getseasons;

import java.util.List;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.season.SeasonDto;

public interface GetSeasonsUseCase extends UseCase<GetSeasonsQuery, Either<UseCaseError, List<SeasonDto>>> {}
