package com.ligitabl.api.shared;

import java.util.Objects;
import java.util.function.Function;

/**
 * Minimal Try implementation for use with Either.liftTry() and Either.fromTry().
 * This version only provides the essential operations needed for Either integration.
 */
public sealed interface Try<T> permits Try.Success, Try.Failure {

    /**
     * Creates a Try by executing the given supplier.
     * Catches any exception and wraps it in a Failure.
     */
    static <T> Try<T> of(CheckedSupplier<T> supplier) {
        try {
            return new Success<>(supplier.get());
        } catch (Throwable t) {
            return new Failure<>(t);
        }
    }

    /**
     * Pattern matching support - the only method Either helpers need.
     * Converts Try to any type by providing handlers for both cases.
     */
    <U> U fold(Function<? super Throwable, ? extends U> onFailure, Function<? super T, ? extends U> onSuccess);

    /**
     * Success case - contains a value.
     */
    record Success<T>(T value) implements Try<T> {

        @Override
        public <U> U fold(
                Function<? super Throwable, ? extends U> onFailure, Function<? super T, ? extends U> onSuccess) {
            return onSuccess.apply(value);
        }
    }

    /**
     * Failure case - contains an exception.
     */
    record Failure<T>(Throwable cause) implements Try<T> {

        public Failure {
            Objects.requireNonNull(cause);
        }

        @Override
        public <U> U fold(
                Function<? super Throwable, ? extends U> onFailure, Function<? super T, ? extends U> onSuccess) {
            return onFailure.apply(cause);
        }
    }

    /**
     * Functional interface for checked exceptions.
     */
    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Throwable;
    }
}
