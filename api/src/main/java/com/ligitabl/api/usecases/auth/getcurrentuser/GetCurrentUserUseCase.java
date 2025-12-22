package com.ligitabl.api.usecases.auth.getcurrentuser;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;

public interface GetCurrentUserUseCase extends UseCase<GetCurrentUserQuery, Either<UseCaseError, UserInfo>> {}
