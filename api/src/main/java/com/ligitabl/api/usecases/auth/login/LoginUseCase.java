package com.ligitabl.api.usecases.auth.login;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;

public interface LoginUseCase extends UseCase<LoginCommand, Either<UseCaseError, LoginResult>> {}
