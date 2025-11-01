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
     * Recover a Left into a Right by mapping the error to a fallback value.
     * Right values are passed through unchanged.
     */
    default Either<L, R> recover(Function<? super L, ? extends R> recoverFn) {
        Objects.requireNonNull(recoverFn, "recoverFn");
        return isRight() ? this : Either.right(recoverFn.apply(getLeft()));
    }

    /**
     * Recover a Left using a function that may still return Left (e.g., another attempt).
     * Right values are passed through unchanged.
     */
    default Either<L, R> recoverWith(Function<? super L, ? extends Either<L, R>> recoverFn) {
        Objects.requireNonNull(recoverFn, "recoverFn");
        return isRight() ? this : Objects.requireNonNull(recoverFn.apply(getLeft()), "recoverFn result");
    }

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
     * Functional interface for suppliers that may throw any Throwable.
     * Replaces the small Try.CheckedSupplier used previously.
     */
    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Throwable;
    }

    // liftTry and its overloads removed — prefer liftException/fromException (they catch Exception but rethrow Error)

    /**
     * NOTE (best practice): Prefer mapping exceptions to a small, well-typed domain error type
     * (for example a sealed `UseCaseError` or `DomainError`), rather than leaking raw
     * `Exception`/`Throwable` values throughout your application. Use the `errorMapper`
     * overloads to normalize exceptions at boundaries.
     */

    /**
     * Helper to compose operations catching Exception (checked + runtime) but NOT Error.
     * Errors are rethrown; useful as a safer default in server apps.
     */
    static <L, R, T> Function<R, Either<L, T>> liftException(
            CheckedFunction<R, T> operation, Function<Exception, L> errorMapper) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(errorMapper, "errorMapper");
        return value -> {
            try {
                return Either.right(operation.apply(value));
            } catch (Error e) {
                throw e; // do not catch Errors
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.left(errorMapper.apply(e));
            } catch (Exception e) {
                return Either.left(errorMapper.apply(e));
            } catch (Throwable t) {
                // very defensive: if a plain Throwable is thrown, treat like Exception
                if (t instanceof Error) {
                    throw (Error) t;
                }
                @SuppressWarnings("unchecked")
                Exception ex = (Exception) t;
                return Either.left(errorMapper.apply(ex));
            }
        };
    }

    /**
     * Catch-all lifting variant that catches ANY Throwable (Exception and Error) and maps it to L.
     * Use sparingly and only at process boundaries where you truly want to swallow Errors.
     */
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

    /** Identity Left = Throwable variant for liftThrowable. */
    @SuppressWarnings("unchecked")
    static <R, T> Function<R, Either<Throwable, T>> liftThrowable(CheckedFunction<R, T> operation) {
        return (Function<R, Either<Throwable, T>>) (Function<?, ?>) liftThrowable(operation, Function.identity());
    }

    /** Identity Left = Exception variant (Errors rethrown). */
    @SuppressWarnings("unchecked")
    static <R, T> Function<R, Either<Exception, T>> liftException(CheckedFunction<R, T> operation) {
        return (Function<R, Either<Exception, T>>) (Function<?, ?>) liftException(operation, Function.identity());
    }

    /** Fixed-left variant for exception-only lifting (Errors rethrown). */
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
                if (t instanceof Error) {
                    throw (Error) t;
                }
                return Either.left(errorValue);
            }
        };
    }

    // fromTry overloads removed — prefer fromException (catch Exception but rethrow Error)

    /**
     * Creates an Either by executing a supplier, catching Exception but NOT Error.
     * Errors are rethrown; InterruptedException preserves the interrupt flag.
     */
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
            @SuppressWarnings("unchecked")
            Exception ex = (Exception) t;
            return Either.left(errorMapper.apply(ex));
        }
    }

    /** Identity Left = Exception variant for fromException (Errors rethrown). */
    static <T> Either<Exception, T> fromException(CheckedSupplier<T> supplier) {
        return fromException(supplier, Function.identity());
    }

    /** Fixed-left variant for fromException (Errors rethrown). */
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

    /**
     * Catch-all supplier variant that catches ANY Throwable and maps it to L.
     * Use sparingly and only at process boundaries where you truly want to swallow Errors.
     */
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

    /** Identity Left = Throwable variant for fromThrowable. */
    static <T> Either<Throwable, T> fromThrowable(CheckedSupplier<T> supplier) {
        return fromThrowable(supplier, Function.identity());
    }

    // ========== Checked-only variants - only catch checked exceptions ==========

    /**
     * Like liftException but ONLY catches checked exceptions (Exception, not RuntimeException/Error).
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
     * Like liftException but ONLY catches checked exceptions, with custom error mapper.
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
     * Like liftException but ONLY catches checked exceptions, with fixed error value.
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
