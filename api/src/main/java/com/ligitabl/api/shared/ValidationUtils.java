package com.ligitabl.api.shared;

import java.util.Optional;

import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.shared.Either;

public class ValidationUtils {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T> Either<UseCaseError, T> requireFound(Optional<T> optional,
        UseCaseError error) {
        return optional.<Either<UseCaseError, T>>map(Either::right)
            .orElseGet(() -> Either.left(error));
    }

    public static <T> Either<UseCaseError, T> require(boolean condition, UseCaseError error,
        T value) {
        return condition ? Either.right(value) : Either.left(error);
    }

    public static <T> Either<UseCaseError, T> requireNot(boolean condition, UseCaseError error,
        T value) {
        return !condition ? Either.right(value) : Either.left(error);
    }
}
