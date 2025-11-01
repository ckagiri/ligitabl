package com.ligitabl.api.shared;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EitherPeekBothTest {

    @Test
    void peekBoth_invokes_left_consumer_on_Left() {
        AtomicReference<String> leftRef = new AtomicReference<>();
        AtomicReference<Integer> rightRef = new AtomicReference<>();

        Either<String, Integer> left = Either.left("err");
        Either<String, Integer> same = left.peekBoth(leftRef::set, rightRef::set);

        assertSame(left, same, "peekBoth should return this for chaining");
        assertEquals("err", leftRef.get());
        assertNull(rightRef.get());
    }

    @Test
    void peekBoth_invokes_right_consumer_on_Right() {
        AtomicReference<String> leftRef = new AtomicReference<>();
        AtomicReference<Integer> rightRef = new AtomicReference<>();

        Either<String, Integer> right = Either.right(42);
        Either<String, Integer> same = right.peekBoth(leftRef::set, rightRef::set);

        assertSame(right, same, "peekBoth should return this for chaining");
        assertNull(leftRef.get());
        assertEquals(42, rightRef.get());
    }
}
