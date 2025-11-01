package com.ligitabl.api.shared.validation;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;

public interface Guard<T> {
    Either<UseCaseError, T> validate(T candidate);
}
