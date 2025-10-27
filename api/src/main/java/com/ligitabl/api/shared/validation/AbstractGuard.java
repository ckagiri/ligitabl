package com.ligitabl.api.shared.validation;

import java.util.function.Supplier;

import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.model.shared.Either;

public abstract class AbstractGuard<T> implements Guard<T> {

    protected Either<UseCaseError, T> ensureIdIsNull(T entity, Supplier<Object> idSupplier) {
        if (idSupplier.get() != null) {
            return Either.left(new ValidationError("ID must be null when creating a new entity"));
        }
        return Either.right(entity);
    }

    protected Either<UseCaseError, T> ensureIdIsNotNull(T entity, Supplier<Object> idSupplier) {
        if (idSupplier.get() == null) {
            return Either.left(new ValidationError("ID must not be null when updating an entity"));
        }
        return Either.right(entity);
    }
}
