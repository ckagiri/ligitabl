package com.ligitabl.api.usecases.match.getroundmatches;

import java.util.List;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.match.MatchDto;

public interface GetRoundMatchesUseCase extends UseCase<GetRoundMatchesQuery, Either<UseCaseError, List<MatchDto>>> {}
