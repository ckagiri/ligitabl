package com.ligitabl.api.usecases.match.getdefaultroundmatches;

import java.util.List;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.match.MatchDto;

public interface GetDefaultRoundMatchesUseCase
        extends UseCase<Void, Either<UseCaseError, List<MatchDto>>> {}
