package com.ligitabl.api.shared;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A monadic container representing a value of one of two possible types (a disjoint union).
 * An Either is either a Left or a Right.
 * By convention, Left is used for failure/error and Right is used for success.
 */
public sealed interface Either<L, R> permits Either.Left, Either.Right {

    // ========== Factory Methods ==========

    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    static <L, R> Either<L, R> ofNullable(R value, L leftValue) {
        return value != null ? right(value) : left(leftValue);
    }

    static <L, R> Either<L, R> ofNullable(R value, Supplier<? extends L> leftSupplier) {
        Objects.requireNonNull(leftSupplier, "leftSupplier");
        return value != null ? right(value) : left(leftSupplier.get());
    }

    static <L, R> Either<L, R> ofOptional(Optional<R> optional, L leftValue) {
        Objects.requireNonNull(optional, "optional");
        return optional.map(Either::<L, R>right).orElse(left(leftValue));
    }

    static <L, R> Either<L, R> ofOptional(Optional<R> optional, Supplier<? extends L> leftSupplier) {
        Objects.requireNonNull(optional, "optional");
        Objects.requireNonNull(leftSupplier, "leftSupplier");
        return optional.map(Either::<L, R>right).orElseGet(() -> left(leftSupplier.get()));
    }

    static <L> Either<L, Unit> combine(java.util.List<? extends Either<L, ?>> eithers) {
        Objects.requireNonNull(eithers, "eithers");
        for (Either<L, ?> either : eithers) {
            if (either.isLeft()) {
                return Either.left(either.getLeft());
            }
        }
        return Either.right(Unit.INSTANCE);
    }

    @SafeVarargs
    static <L> Either<L, Unit> combine(Either<L, ?>... eithers) {
        Objects.requireNonNull(eithers, "eithers");
        return combine(java.util.Arrays.asList(eithers));
    }

    // ========== Core Query Methods ==========

    boolean isLeft();

    boolean isRight();

    L getLeft();

    R get();

    default R getRight() {
        return get();
    }

    default R getValue() {
        return get();
    }

    default L getError() {
        return getLeft();
    }

    // ========== Transformation Methods ==========

    <T> Either<L, T> map(Function<? super R, ? extends T> mapper);

    <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> mapper);

    <T> Either<T, R> mapLeft(Function<? super L, ? extends T> mapper);

    Either<R, L> swap();

    <T, U> Either<T, U> bimap(
            Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends U> rightMapper);

    // ========== Filtering ==========

    Either<L, R> filterOrElse(Predicate<? super R> predicate, Supplier<? extends L> leftSupplier);

    default Either<L, R> filter(Predicate<? super R> predicate, Function<? super R, ? extends L> errorProvider) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(errorProvider, "errorProvider");
        return flatMap(value -> predicate.test(value) ? Either.right(value) : Either.left(errorProvider.apply(value)));
    }

    // ========== Recovery Methods ==========

    R getOrElse(R defaultValue);

    R getOrElse(Supplier<? extends R> supplier);

    Either<L, R> orElse(Either<L, R> other);

    Either<L, R> orElse(Supplier<? extends Either<L, R>> supplier);

    default Either<L, R> recover(Function<? super L, ? extends R> recoverFn) {
        Objects.requireNonNull(recoverFn, "recoverFn");
        return isRight() ? this : Either.right(recoverFn.apply(getLeft()));
    }

    default Either<L, R> recoverWith(Function<? super L, ? extends Either<L, R>> recoverFn) {
        Objects.requireNonNull(recoverFn, "recoverFn");
        return isRight() ? this : Objects.requireNonNull(recoverFn.apply(getLeft()), "recoverFn result");
    }

    // ========== Side-Effect Methods ==========

    Either<L, R> peek(Consumer<? super R> action);

    Either<L, R> peekLeft(Consumer<? super L> action);

    /**
     * Invoke the appropriate consumer based on whether this is Left or Right.
     * Useful for executing different side effects based on success/failure.
     * Returns this for chaining.
     *
     * Usage: either.peekBoth(
     *     error -> log.error("Failed: {}", error),
     *     value -> log.info("Success: {}", value)
     * )
     */
    default Either<L, R> peekBoth(Consumer<? super L> leftAction, Consumer<? super R> rightAction) {
        Objects.requireNonNull(leftAction, "leftAction");
        Objects.requireNonNull(rightAction, "rightAction");
        if (isLeft()) {
            leftAction.accept(getLeft());
        } else {
            rightAction.accept(get());
        }
        return this;
    }

    default Either<L, R> peekIf(Predicate<? super R> predicate, Consumer<? super R> action) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(action, "action");
        return peek(value -> {
            if (predicate.test(value)) {
                action.accept(value);
            }
        });
    }

    default Either<L, R> logLeft(String operation, org.slf4j.Logger logger) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(logger, "logger");
        return peekLeft(error -> logger.error("{} failed: {}", operation, error));
    }

    default Either<L, R> logLeft(String operation, org.slf4j.Logger logger, LogLevel level) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(level, "level");
        return peekLeft(error -> {
            switch (level) {
                case ERROR -> logger.error("{} failed: {}", operation, error);
                case WARN -> logger.warn("{} failed: {}", operation, error);
                case INFO -> logger.info("{} failed: {}", operation, error);
                case DEBUG -> logger.debug("{} failed: {}", operation, error);
            }
        });
    }

    default Either<L, R> logLeftWithException(String operation, org.slf4j.Logger logger) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(logger, "logger");
        return peekLeft(error -> {
            if (error instanceof Throwable t) {
                logger.error("{} failed", operation, t);
            } else {
                logger.error("{} failed: {}", operation, error);
            }
        });
    }

    default Either<L, R> logRight(String operation, org.slf4j.Logger logger) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(logger, "logger");
        return peek(value -> logger.info("{} succeeded: {}", operation, value));
    }

    default Either<L, R> logRight(String operation, org.slf4j.Logger logger, LogLevel level) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(level, "level");
        return peek(value -> {
            switch (level) {
                case ERROR -> logger.error("{} succeeded: {}", operation, value);
                case WARN -> logger.warn("{} succeeded: {}", operation, value);
                case INFO -> logger.info("{} succeeded: {}", operation, value);
                case DEBUG -> logger.debug("{} succeeded: {}", operation, value);
            }
        });
    }

    enum LogLevel {
        ERROR,
        WARN,
        INFO,
        DEBUG
    }

    // ========== Conversion Methods ==========

    Optional<R> toOptional();

    Optional<L> toOptionalLeft();

    default Optional<R> toOptional(Function<? super L, ? extends R> leftMapper) {
        Objects.requireNonNull(leftMapper, "leftMapper");
        return isRight() ? Optional.ofNullable(get()) : Optional.ofNullable(leftMapper.apply(getLeft()));
    }

    // ========== Pattern Matching ==========

    <T> T fold(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper);

    default <T, C> T foldWithContext(
            C context,
            java.util.function.BiFunction<? super L, ? super C, ? extends T> leftMapper,
            java.util.function.BiFunction<? super R, ? super C, ? extends T> rightMapper) {
        Objects.requireNonNull(leftMapper, "leftMapper");
        Objects.requireNonNull(rightMapper, "rightMapper");
        return isRight() ? rightMapper.apply(get(), context) : leftMapper.apply(getLeft(), context);
    }

    // ========== Functional Interfaces ==========

    @FunctionalInterface
    interface CheckedFunction<T, R> {
        R apply(T value) throws Throwable;
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Throwable;
    }

    @FunctionalInterface
    interface CheckedOnlyFunction<T, R> {
        R apply(T value) throws Exception;
    }

    @FunctionalInterface
    interface CheckedOnlySupplier<T> {
        T get() throws Exception;
    }

    // ========== Exception Lifting (Recommended Default) ==========

    static <L, R, T> Function<R, Either<L, T>> liftException(
            CheckedFunction<R, T> operation, Function<Exception, L> errorMapper) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(errorMapper, "errorMapper");
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (Error e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.left(errorMapper.apply(e));
            } catch (Exception e) {
                return Either.left(errorMapper.apply(e));
            } catch (Throwable t) {
                if (t instanceof Error) throw (Error) t;
                Exception ex = (Exception) t;
                return Either.left(errorMapper.apply(ex));
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <R, T> Function<R, Either<Exception, T>> liftException(CheckedFunction<R, T> operation) {
        return (Function<R, Either<Exception, T>>) (Function<?, ?>) liftException(operation, Function.identity());
    }

    static <L, R, T> Function<R, Either<L, T>> liftException(CheckedFunction<R, T> operation, L errorValue) {
        Objects.requireNonNull(operation, "operation");
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (Error e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.left(errorValue);
            } catch (Exception e) {
                return Either.left(errorValue);
            } catch (Throwable t) {
                if (t instanceof Error) throw (Error) t;
                return Either.left(errorValue);
            }
        };
    }

    static <L, T> Either<L, T> fromException(CheckedSupplier<T> supplier, Function<Exception, L> errorMapper) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(errorMapper, "errorMapper");
        try {
            return Either.right(supplier.get());
        } catch (Error e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Either.left(errorMapper.apply(e));
        } catch (Exception e) {
            return Either.left(errorMapper.apply(e));
        } catch (Throwable t) {
            if (t instanceof Error) throw (Error) t;
            Exception ex = (Exception) t;
            return Either.left(errorMapper.apply(ex));
        }
    }

    static <T> Either<Exception, T> fromException(CheckedSupplier<T> supplier) {
        return fromException(supplier, Function.identity());
    }

    static <L, T> Either<L, T> fromException(CheckedSupplier<T> supplier, L errorValue) {
        Objects.requireNonNull(supplier, "supplier");
        try {
            return Either.right(supplier.get());
        } catch (Error e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Either.left(errorValue);
        } catch (Exception e) {
            return Either.left(errorValue);
        } catch (Throwable t) {
            if (t instanceof Error) throw (Error) t;
            return Either.left(errorValue);
        }
    }

    // ========== Throwable Lifting (Use Sparingly) ==========

    static <L, R, T> Function<R, Either<L, T>> liftThrowable(
            CheckedFunction<R, T> operation, Function<Throwable, L> errorMapper) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(errorMapper, "errorMapper");
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.left(errorMapper.apply(e));
            } catch (Throwable t) {
                return Either.left(errorMapper.apply(t));
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <R, T> Function<R, Either<Throwable, T>> liftThrowable(CheckedFunction<R, T> operation) {
        return (Function<R, Either<Throwable, T>>) (Function<?, ?>) liftThrowable(operation, Function.identity());
    }

    static <L, T> Either<L, T> fromThrowable(CheckedSupplier<T> supplier, Function<Throwable, L> errorMapper) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(errorMapper, "errorMapper");
        try {
            return Either.right(supplier.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Either.left(errorMapper.apply(e));
        } catch (Throwable t) {
            return Either.left(errorMapper.apply(t));
        }
    }

    static <T> Either<Throwable, T> fromThrowable(CheckedSupplier<T> supplier) {
        return fromThrowable(supplier, Function.identity());
    }

    // ========== Checked-Only Lifting (Fail-Fast) ==========

    static <R, T> Function<R, Either<Exception, T>> liftChecked(CheckedOnlyFunction<R, T> operation) {
        Objects.requireNonNull(operation, "operation");
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                return Either.left(e);
            }
        };
    }

    static <L, R, T> Function<R, Either<L, T>> liftChecked(
            CheckedOnlyFunction<R, T> operation, Function<Exception, L> errorMapper) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(errorMapper, "errorMapper");
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                return Either.left(errorMapper.apply(e));
            }
        };
    }

    static <L, R, T> Function<R, Either<L, T>> liftChecked(CheckedOnlyFunction<R, T> operation, L errorValue) {
        Objects.requireNonNull(operation, "operation");
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                return Either.left(errorValue);
            }
        };
    }

    static <T> Either<Exception, T> fromChecked(CheckedOnlySupplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        try {
            return Either.right(supplier.get());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            return Either.left(e);
        }
    }

    static <L, T> Either<L, T> fromChecked(CheckedOnlySupplier<T> supplier, Function<Exception, L> errorMapper) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(errorMapper, "errorMapper");
        try {
            return Either.right(supplier.get());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            return Either.left(errorMapper.apply(e));
        }
    }

    static <L, T> Either<L, T> fromChecked(CheckedOnlySupplier<T> supplier, L errorValue) {
        Objects.requireNonNull(supplier, "supplier");
        try {
            return Either.right(supplier.get());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            return Either.left(errorValue);
        }
    }

    // ========== Implementations ==========

    record Left<L, R>(L value) implements Either<L, R> {

        public Left {
            Objects.requireNonNull(value, "Left value cannot be null");
        }

        @Override
        public boolean isLeft() {
            return true;
        }

        @Override
        public boolean isRight() {
            return false;
        }

        @Override
        public L getLeft() {
            return value;
        }

        @Override
        public R get() {
            throw new NoSuchElementException("Cannot get() on Left");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Either<L, T> map(Function<? super R, ? extends T> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return (Either<L, T>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return (Either<L, T>) this;
        }

        @Override
        public <T> Either<T, R> mapLeft(Function<? super L, ? extends T> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return new Left<>(mapper.apply(value));
        }

        @Override
        public Either<R, L> swap() {
            return new Right<>(value);
        }

        @Override
        public Either<L, R> filterOrElse(Predicate<? super R> predicate, Supplier<? extends L> leftSupplier) {
            Objects.requireNonNull(predicate, "predicate");
            Objects.requireNonNull(leftSupplier, "leftSupplier");
            return this;
        }

        @Override
        public R getOrElse(R defaultValue) {
            return defaultValue;
        }

        @Override
        public R getOrElse(Supplier<? extends R> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return supplier.get();
        }

        @Override
        public Either<L, R> orElse(Either<L, R> other) {
            Objects.requireNonNull(other, "other");
            return other;
        }

        @Override
        public Either<L, R> orElse(Supplier<? extends Either<L, R>> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return supplier.get();
        }

        @Override
        public Either<L, R> peek(Consumer<? super R> action) {
            Objects.requireNonNull(action, "action");
            return this;
        }

        @Override
        public Either<L, R> peekLeft(Consumer<? super L> action) {
            Objects.requireNonNull(action, "action");
            action.accept(value);
            return this;
        }

        @Override
        public Optional<R> toOptional() {
            return Optional.empty();
        }

        @Override
        public Optional<L> toOptionalLeft() {
            return Optional.of(value);
        }

        @Override
        public <T> T fold(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
            Objects.requireNonNull(leftMapper, "leftMapper");
            Objects.requireNonNull(rightMapper, "rightMapper");
            return leftMapper.apply(value);
        }

        @Override
        public <T, U> Either<T, U> bimap(
                Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends U> rightMapper) {
            Objects.requireNonNull(leftMapper, "leftMapper");
            Objects.requireNonNull(rightMapper, "rightMapper");
            return new Left<>(leftMapper.apply(value));
        }
    }

    record Right<L, R>(R value) implements Either<L, R> {

        @Override
        public boolean isLeft() {
            return false;
        }

        @Override
        public boolean isRight() {
            return true;
        }

        @Override
        public L getLeft() {
            throw new NoSuchElementException("Cannot getLeft() on Right");
        }

        @Override
        public R get() {
            return value;
        }

        @Override
        public <T> Either<L, T> map(Function<? super R, ? extends T> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return new Right<>(mapper.apply(value));
        }

        @Override
        public <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return mapper.apply(value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Either<T, R> mapLeft(Function<? super L, ? extends T> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return (Either<T, R>) this;
        }

        @Override
        public Either<R, L> swap() {
            return new Left<>(value);
        }

        @Override
        public Either<L, R> filterOrElse(Predicate<? super R> predicate, Supplier<? extends L> leftSupplier) {
            Objects.requireNonNull(predicate, "predicate");
            Objects.requireNonNull(leftSupplier, "leftSupplier");
            return predicate.test(value) ? this : new Left<>(leftSupplier.get());
        }

        @Override
        public R getOrElse(R defaultValue) {
            return value;
        }

        @Override
        public R getOrElse(Supplier<? extends R> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return value;
        }

        @Override
        public Either<L, R> orElse(Either<L, R> other) {
            Objects.requireNonNull(other, "other");
            return this;
        }

        @Override
        public Either<L, R> orElse(Supplier<? extends Either<L, R>> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return this;
        }

        @Override
        public Either<L, R> peek(Consumer<? super R> action) {
            Objects.requireNonNull(action, "action");
            action.accept(value);
            return this;
        }

        @Override
        public Either<L, R> peekLeft(Consumer<? super L> action) {
            Objects.requireNonNull(action, "action");
            return this;
        }

        @Override
        public Optional<R> toOptional() {
            return Optional.ofNullable(value);
        }

        @Override
        public Optional<L> toOptionalLeft() {
            return Optional.empty();
        }

        @Override
        public <T> T fold(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
            Objects.requireNonNull(leftMapper, "leftMapper");
            Objects.requireNonNull(rightMapper, "rightMapper");
            return rightMapper.apply(value);
        }

        @Override
        public <T, U> Either<T, U> bimap(
                Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends U> rightMapper) {
            Objects.requireNonNull(leftMapper, "leftMapper");
            Objects.requireNonNull(rightMapper, "rightMapper");
            return new Right<>(rightMapper.apply(value));
        }
    }
}
