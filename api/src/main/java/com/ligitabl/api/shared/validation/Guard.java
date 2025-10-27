package com.ligitabl.api.shared.validation;

import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.shared.Either;

public interface Guard<T> {
    Either<UseCaseError, T> forCreate(T candidate);
    Either<UseCaseError, T> forUpdate(T candidate);
}
