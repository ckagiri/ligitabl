package com.ligitabl.api.shared;

import java.util.Optional;
import java.util.UUID;

import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.shared.Identifiable;

public class ValidationUtils {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T> Either<UseCaseError, T> requireFound(Optional<T> optional, UseCaseError error) {
        return optional.<Either<UseCaseError, T>>map(Either::right).orElseGet(() -> Either.left(error));
    }

    public static <T> Either<UseCaseError, T> requireUnique(boolean alreadyExists, T value, UseCaseError error) {
        if (alreadyExists) {
            return Either.left(error);
        }
        return Either.right(value);
    }

    public static Either<UseCaseError, UUID> requireExists(boolean exists, UUID value, UseCaseError error) {
        return exists ? Either.right(value) : Either.left(error);
    }

    public static <T> Either<UseCaseError, T> requireExists(boolean exists, T value, UseCaseError error) {
        return exists ? Either.right(value) : Either.left(error);
    }

    public static <T> Either<UseCaseError, T> require(boolean condition, UseCaseError error, T value) {
        return condition ? Either.right(value) : Either.left(error);
    }

    public static <T> Either<UseCaseError, T> requireNot(boolean condition, UseCaseError error, T value) {
        return !condition ? Either.right(value) : Either.left(error);
    }

    public static <T extends Identifiable<?>> Either<UseCaseError, T> requireIdIsNull(T entity) {
        return entity.getId() == null
                ? Either.right(entity)
                : Either.left(UseCaseErrors.validation("id", "ID must be null when creating"));
    }

    public static <T extends Identifiable<?>> Either<UseCaseError, T> requireIdIsNotNull(T entity) {
        return entity.getId() != null
                ? Either.right(entity)
                : Either.left(UseCaseErrors.validation("id", "ID must not be null when updating"));
    }
}
