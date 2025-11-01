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

    /**
     * Creates a Left (typically error/failure case).
     */
    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    /**
     * Creates a Right (typically success case).
     */
    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    /**
     * Creates an Either from a nullable value.
     * Returns Right if non-null, Left with provided left value if null.
     */
    static <L, R> Either<L, R> ofNullable(R value, L leftValue) {
        return value != null ? right(value) : left(leftValue);
    }

    /**
     * Creates an Either from a nullable value with supplier.
     */
    static <L, R> Either<L, R> ofNullable(R value, Supplier<? extends L> leftSupplier) {
        return value != null ? right(value) : left(leftSupplier.get());
    }

    /**
     * Creates an Either from an Optional.
     */
    static <L, R> Either<L, R> ofOptional(Optional<R> optional, L leftValue) {
        return optional.map(Either::<L, R>right).orElse(left(leftValue));
    }

    /**
     * Creates an Either from an Optional with supplier.
     */
    static <L, R> Either<L, R> ofOptional(Optional<R> optional, Supplier<? extends L> leftSupplier) {
        return optional.map(Either::<L, R>right).orElseGet(() -> left(leftSupplier.get()));
    }

    /**
     * Combines multiple Either instances, failing fast on the first Left.
     * Returns Either<L, Unit> where Unit represents successful completion of all operations.
     *
     * Useful for validating multiple conditions or performing multiple operations
     * where you only care about success/failure, not the individual results.
     *
     * Usage:
     * <pre>
     * Either<String, Unit> result = Either.combine(List.of(
     *     validateEmail(email),
     *     validatePassword(password),
     *     validateAge(age)
     * ));
     * </pre>
     *
     * @param eithers List of Either instances to combine
     * @return Right(Unit) if all are Right, otherwise the first Left
     */
    static <L> Either<L, Unit> combine(java.util.List<? extends Either<L, ?>> eithers) {
        for (Either<L, ?> either : eithers) {
            if (either.isLeft()) {
                return Either.left(either.getLeft());
            }
        }
        return Either.right(Unit.INSTANCE);
    }

    /**
     * Combines multiple Either instances with varargs for convenience.
     */
    @SafeVarargs
    static <L> Either<L, Unit> combine(Either<L, ?>... eithers) {
        return combine(java.util.Arrays.asList(eithers));
    }

    // Core query methods
    boolean isLeft();

    boolean isRight();

    L getLeft();

    R get();

    /**
     * Alias for get() - more semantically clear when Right represents a value.
     */
    default R getRight() {
        return get();
    }

    /**
     * Alias for get() - emphasizes Right as the success value.
     */
    default R getValue() {
        return get();
    }

    /**
     * Alias for getLeft() - emphasizes Left as the error value.
     */
    default L getError() {
        return getLeft();
    }

    // Transformation methods (right-biased)
    <T> Either<L, T> map(Function<? super R, ? extends T> mapper);

    <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> mapper);

    <T> Either<T, R> mapLeft(Function<? super L, ? extends T> mapper);

    Either<R, L> swap();

    // Filtering
    Either<L, R> filterOrElse(Predicate<? super R> predicate, Supplier<? extends L> leftSupplier);

    // Recovery/fallback methods
    R getOrElse(R defaultValue);

    R getOrElse(Supplier<? extends R> supplier);

    Either<L, R> orElse(Either<L, R> other);

    Either<L, R> orElse(Supplier<? extends Either<L, R>> supplier);

    // Side-effect methods
    Either<L, R> peek(Consumer<? super R> action);

    Either<L, R> peekLeft(Consumer<? super L> action);

    /**
     * Logs the left value if present using the provided logger.
     * Convenient method for logging errors without writing peekLeft boilerplate.
     *
     * Usage: .logLeft("User loading", log)
     *
     * @param operation Description of the operation that failed
     * @param logger SLF4J logger to use
     * @return This Either for chaining
     */
    default Either<L, R> logLeft(String operation, org.slf4j.Logger logger) {
        return peekLeft(error -> logger.error("{} failed: {}", operation, error));
    }

    /**
     * Logs the left value if present with custom log level.
     *
     * @param operation Description of the operation
     * @param logger SLF4J logger to use
     * @param level Log level (ERROR, WARN, INFO, DEBUG)
     * @return This Either for chaining
     */
    default Either<L, R> logLeft(String operation, org.slf4j.Logger logger, LogLevel level) {
        return peekLeft(error -> {
            switch (level) {
                case ERROR -> logger.error("{} failed: {}", operation, error);
                case WARN -> logger.warn("{} failed: {}", operation, error);
                case INFO -> logger.info("{} failed: {}", operation, error);
                case DEBUG -> logger.debug("{} failed: {}", operation, error);
            }
        });
    }

    /**
     * Logs the left value with the throwable if L is a Throwable or contains one.
     *
     * @param operation Description of the operation
     * @param logger SLF4J logger to use
     * @return This Either for chaining
     */
    default Either<L, R> logLeftWithException(String operation, org.slf4j.Logger logger) {
        return peekLeft(error -> {
            if (error instanceof Throwable t) {
                logger.error("{} failed", operation, t);
            } else {
                logger.error("{} failed: {}", operation, error);
            }
        });
    }

    enum LogLevel {
        ERROR,
        WARN,
        INFO,
        DEBUG
    }

    // Conversion methods
    Optional<R> toOptional();

    Optional<L> toOptionalLeft();

    // Pattern matching support
    <T> T fold(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper);

    // Bi-map (transform both sides)
    <T, U> Either<T, U> bimap(
            Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends U> rightMapper);

    /**
     * Left case - typically represents an error or failure.
     */
    record Left<L, R>(L value) implements Either<L, R> {

        public Left {
            Objects.requireNonNull(value);
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
            return (Either<L, T>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> mapper) {
            return (Either<L, T>) this;
        }

        @Override
        public <T> Either<T, R> mapLeft(Function<? super L, ? extends T> mapper) {
            Objects.requireNonNull(mapper);
            return new Left<>(mapper.apply(value));
        }

        @Override
        public Either<R, L> swap() {
            return new Right<>(value);
        }

        @Override
        public Either<L, R> filterOrElse(Predicate<? super R> predicate, Supplier<? extends L> leftSupplier) {
            return this;
        }

        @Override
        public R getOrElse(R defaultValue) {
            return defaultValue;
        }

        @Override
        public R getOrElse(Supplier<? extends R> supplier) {
            Objects.requireNonNull(supplier);
            return supplier.get();
        }

        @Override
        public Either<L, R> orElse(Either<L, R> other) {
            return other;
        }

        @Override
        public Either<L, R> orElse(Supplier<? extends Either<L, R>> supplier) {
            Objects.requireNonNull(supplier);
            return supplier.get();
        }

        @Override
        public Either<L, R> peek(Consumer<? super R> action) {
            return this;
        }

        @Override
        public Either<L, R> peekLeft(Consumer<? super L> action) {
            Objects.requireNonNull(action);
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
            return leftMapper.apply(value);
        }

        @Override
        public <T, U> Either<T, U> bimap(
                Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends U> rightMapper) {
            return new Left<>(leftMapper.apply(value));
        }
    }

    /**
     * Right case - typically represents a success value.
     */
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
            Objects.requireNonNull(mapper);
            return new Right<>(mapper.apply(value));
        }

        @Override
        public <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> mapper) {
            Objects.requireNonNull(mapper);
            return mapper.apply(value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Either<T, R> mapLeft(Function<? super L, ? extends T> mapper) {
            return (Either<T, R>) this;
        }

        @Override
        public Either<R, L> swap() {
            return new Left<>(value);
        }

        @Override
        public Either<L, R> filterOrElse(Predicate<? super R> predicate, Supplier<? extends L> leftSupplier) {
            Objects.requireNonNull(predicate);
            Objects.requireNonNull(leftSupplier);
            return predicate.test(value) ? this : new Left<>(leftSupplier.get());
        }

        @Override
        public R getOrElse(R defaultValue) {
            return value;
        }

        @Override
        public R getOrElse(Supplier<? extends R> supplier) {
            return value;
        }

        @Override
        public Either<L, R> orElse(Either<L, R> other) {
            return this;
        }

        @Override
        public Either<L, R> orElse(Supplier<? extends Either<L, R>> supplier) {
            return this;
        }

        @Override
        public Either<L, R> peek(Consumer<? super R> action) {
            Objects.requireNonNull(action);
            action.accept(value);
            return this;
        }

        @Override
        public Either<L, R> peekLeft(Consumer<? super L> action) {
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
            return rightMapper.apply(value);
        }

        @Override
        public <T, U> Either<T, U> bimap(
                Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends U> rightMapper) {
            return new Right<>(rightMapper.apply(value));
        }
    }

    /**
     * Functional interface for operations that may throw exceptions.
     * Used with liftTry to compose Try operations within Either chains.
     */
    @FunctionalInterface
    interface CheckedFunction<T, R> {
        R apply(T value) throws Throwable;
    }

    /**
     * Functional interface for binary operations that may throw exceptions.
     */
    @FunctionalInterface
    interface CheckedBiFunction<T, U, R> {
        R apply(T t, U u) throws Throwable;
    }

    /**
     * Functional interface for operations that only throw checked exceptions.
     * Use with liftChecked/fromChecked when you want unchecked exceptions to propagate.
     */
    @FunctionalInterface
    interface CheckedOnlyFunction<T, R> {
        R apply(T value) throws Exception;
    }

    /**
     * Functional interface for suppliers that only throw checked exceptions.
     * Use with liftChecked/fromChecked when you want unchecked exceptions to propagate.
     */
    @FunctionalInterface
    interface CheckedOnlySupplier<T> {
        T get() throws Exception;
    }

    /**
     * Helper to compose Try operations within Either chains.
     * Catches ALL exceptions (checked and unchecked).
     *
     * This is the DEFAULT - use when you want to catch everything.
     *
     * Usage: either.flatMap(Either.liftTry(riskyOp, error -> "Failed: " + error))
     *
     * @param operation The operation that may throw
     * @param errorMapper Function to map Throwable to left type
     * @return Function that returns Either<L, T>
     */
    static <L, R, T> Function<R, Either<L, T>> liftTry(
            CheckedFunction<R, T> operation, Function<Throwable, L> errorMapper) {
        return value -> Try.of(() -> operation.apply(value))
                .fold(error -> Either.left(errorMapper.apply(error)), Either::right);
    }

    /**
     * Helper to compose Try operations with identity error mapping.
     * Catches ALL exceptions (checked and unchecked).
     * The exception itself becomes the Left value (Either<Throwable, T>).
     *
     * This is the DEFAULT - use when you want to catch everything.
     *
     * Usage: either.flatMap(Either.liftTry(riskyOp))
     *
     * @param operation The operation that may throw
     * @return Function that returns Either<Throwable, T>
     */
    @SuppressWarnings("unchecked")
    static <R, T> Function<R, Either<Throwable, T>> liftTry(CheckedFunction<R, T> operation) {
        return (Function<R, Either<Throwable, T>>) liftTry(operation, error -> error);
    }

    /**
     * Helper to compose Try operations with a fixed left value on error.
     * Catches ALL exceptions (checked and unchecked).
     *
     * This is the DEFAULT - use when you want to catch everything.
     *
     * Usage: either.flatMap(Either.liftTry(riskyOp, ErrorCode.FAILED))
     *
     * @param operation The operation that may throw
     * @param errorValue Fixed error value to use on exception
     * @return Function that returns Either<L, T>
     */
    static <L, R, T> Function<R, Either<L, T>> liftTry(CheckedFunction<R, T> operation, L errorValue) {
        return value -> Try.of(() -> operation.apply(value)).fold(error -> Either.left(errorValue), Either::right);
    }

    /**
     * Creates an Either from a Try operation that doesn't depend on a previous value.
     * Catches ALL exceptions (checked and unchecked).
     *
     * This is the DEFAULT - use when you want to catch everything.
     *
     * Usage: Either.fromTry(() -> readFile(), error -> "Read failed")
     *
     * @param supplier The operation that may throw
     * @param errorMapper Function to map Throwable to left type
     * @return Either<L, T>
     */
    static <L, T> Either<L, T> fromTry(Try.CheckedSupplier<T> supplier, Function<Throwable, L> errorMapper) {
        return Try.of(supplier).fold(error -> Either.left(errorMapper.apply(error)), Either::right);
    }

    /**
     * Creates an Either from a Try operation with identity error mapping.
     * Catches ALL exceptions (checked and unchecked).
     * The exception itself becomes the Left value (Either<Throwable, T>).
     *
     * This is the DEFAULT - use when you want to catch everything.
     *
     * Usage: Either.fromTry(() -> readFile())
     *
     * @param supplier The operation that may throw
     * @return Either<Throwable, T>
     */
    static <T> Either<Throwable, T> fromTry(Try.CheckedSupplier<T> supplier) {
        return fromTry(supplier, error -> error);
    }

    /**
     * Creates an Either from a Try operation with a fixed error value.
     * Catches ALL exceptions (checked and unchecked).
     *
     * This is the DEFAULT - use when you want to catch everything.
     *
     * Usage: Either.fromTry(() -> readFile(), ErrorCode.IO_ERROR)
     *
     * @param supplier The operation that may throw
     * @param errorValue Fixed error value to use on exception
     * @return Either<L, T>
     */
    static <L, T> Either<L, T> fromTry(Try.CheckedSupplier<T> supplier, L errorValue) {
        return Try.of(supplier).fold(error -> Either.left(errorValue), Either::right);
    }

    // ========== Checked-only variants - only catch checked exceptions ==========

    /**
     * Like liftTry but ONLY catches checked exceptions (Exception, not RuntimeException/Error).
     * Unchecked exceptions (RuntimeException, Error) propagate normally - they crash.
     *
     * Use this for FAIL-FAST when you want bugs to crash immediately.
     * Use this for I/O operations where you trust your code and only expect IOException/SQLException.
     *
     * Usage: either.flatMap(Either.liftChecked(this::riskyOperation))
     *
     * @param operation The operation that may throw checked exceptions
     * @return Function that returns Either<Throwable, T>
     */
    static <R, T> Function<R, Either<Throwable, T>> liftChecked(CheckedOnlyFunction<R, T> operation) {
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (RuntimeException | Error e) {
                throw e; // Propagate unchecked exceptions
            } catch (Exception e) {
                return Either.left(e); // Catch checked exceptions only
            }
        };
    }

    /**
     * Like liftTry but ONLY catches checked exceptions, with custom error mapper.
     *
     * Usage: either.flatMap(Either.liftChecked(operation, e -> "Failed: " + e))
     */
    static <L, R, T> Function<R, Either<L, T>> liftChecked(
            CheckedOnlyFunction<R, T> operation, Function<Exception, L> errorMapper) {
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (RuntimeException | Error e) {
                throw e; // Propagate unchecked exceptions
            } catch (Exception e) {
                return Either.left(errorMapper.apply(e));
            }
        };
    }

    /**
     * Like liftTry but ONLY catches checked exceptions, with fixed error value.
     */
    static <L, R, T> Function<R, Either<L, T>> liftChecked(CheckedOnlyFunction<R, T> operation, L errorValue) {
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (RuntimeException | Error e) {
                throw e; // Propagate unchecked exceptions
            } catch (Exception e) {
                return Either.left(errorValue);
            }
        };
    }

    /**
     * Like fromTry but ONLY catches checked exceptions.
     * Unchecked exceptions (RuntimeException, Error) propagate normally - they crash.
     *
     * Use this for FAIL-FAST when you want bugs to crash immediately.
     *
     * Usage: Either.fromChecked(() -> readFile())
     */
    static <T> Either<Throwable, T> fromChecked(CheckedOnlySupplier<T> supplier) {
        try {
            return Either.right(supplier.get());
        } catch (RuntimeException | Error e) {
            throw e; // Propagate unchecked exceptions
        } catch (Exception e) {
            return Either.left(e);
        }
    }

    /**
     * Like fromTry but ONLY catches checked exceptions, with custom error mapper.
     */
    static <L, T> Either<L, T> fromChecked(CheckedOnlySupplier<T> supplier, Function<Exception, L> errorMapper) {
        try {
            return Either.right(supplier.get());
        } catch (RuntimeException | Error e) {
            throw e; // Propagate unchecked exceptions
        } catch (Exception e) {
            return Either.left(errorMapper.apply(e));
        }
    }

    /**
     * Like fromTry but ONLY catches checked exceptions, with fixed error value.
     */
    static <L, T> Either<L, T> fromChecked(CheckedOnlySupplier<T> supplier, L errorValue) {
        try {
            return Either.right(supplier.get());
        } catch (RuntimeException | Error e) {
            throw e; // Propagate unchecked exceptions
        } catch (Exception e) {
            return Either.left(errorValue);
        }
    }
}
