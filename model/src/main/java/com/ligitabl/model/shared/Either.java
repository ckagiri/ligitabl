package com.ligitabl.model.shared;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface Either<L, R> permits Either.Left, Either.Right {

    boolean isLeft();

    boolean isRight();

    L getLeft();

    R getRight();

    default R getValue() {
        return getRight();
    }

    default L getError() {
        return getLeft();
    }

    // --- Functional combinators ---
    default <T> Either<L, T> map(Function<? super R, ? extends T> mapper) {
        return flatMap(r -> right(mapper.apply(r)));
    }

    default <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> mapper) {
        if (this instanceof Right<L, R> right) {
            return mapper.apply(right.value());
        }
        @SuppressWarnings("unchecked")
        Either<L, T> left = (Either<L, T>) this;
        return left;
    }

    // --- transform the Left side ---
    default <L2> Either<L2, R> mapLeft(Function<? super L, ? extends L2> mapper) {
        if (this instanceof Left<L, R> left) {
            return Either.left(mapper.apply(left.value()));
        }
        @SuppressWarnings("unchecked")
        Either<L2, R> right = (Either<L2, R>) this;
        return right;
    }

    /**
     * Transform both sides of the Either. If Left, apply leftMapper; if Right,
     * apply rightMapper.
     */
    default <L2, R2> Either<L2, R2> bimap(
            Function<? super L, ? extends L2> leftMapper, Function<? super R, ? extends R2> rightMapper) {
        if (this instanceof Left<L, R> left) {
            return Either.left(leftMapper.apply(left.value()));
        } else {
            R value = ((Right<L, R>) this).value();
            return Either.right(rightMapper.apply(value));
        }
    }

    default <T> T fold(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
        return this instanceof Left<L, R> left
                ? leftMapper.apply(left.value())
                : rightMapper.apply(((Right<L, R>) this).value());
    }

    // --- Fluent side-effect helpers ---
    default Either<L, R> peek(Consumer<? super R> action) {
        if (this instanceof Right<L, R> right) action.accept(right.value());
        return this;
    }

    default Either<L, R> peekLeft(Consumer<? super L> action) {
        if (this instanceof Left<L, R> left) action.accept(left.value());
        return this;
    }

    // --- Factory methods ---
    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    // --- Combine: fail fast on first Left ---
    static <L> Either<L, Unit> combine(List<? extends Either<L, ?>> eithers) {
        for (Either<L, ?> either : eithers) {
            if (either.isLeft()) {
                return Either.left(either.getLeft());
            }
        }
        return Either.right(Unit.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    static <Super, Sub extends Super, R> Either<Super, R> widenLeft(Either<Sub, R> either) {
        return (Either<Super, R>) either;
    }

    // --- Variants ---
    record Left<L, R>(L value) implements Either<L, R> {
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
        public R getRight() {
            throw new IllegalStateException("No Right value present");
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
            throw new IllegalStateException("No Left value present");
        }

        @Override
        public R getRight() {
            return value;
        }
    }
}
