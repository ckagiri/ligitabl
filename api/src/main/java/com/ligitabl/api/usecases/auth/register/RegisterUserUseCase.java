package com.ligitabl.api.usecases.auth.register;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;

public interface RegisterUserUseCase extends UseCase<RegisterCommand, Either<UseCaseError, RegisterResult>> {}
