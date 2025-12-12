package com.ligitabl.api.usecases.season.getseasonbyslug;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.season.SeasonDto;

public interface GetSeasonBySlugUseCase extends UseCase<GetSeasonBySlugQuery, Either<UseCaseError, SeasonDto>> {}
