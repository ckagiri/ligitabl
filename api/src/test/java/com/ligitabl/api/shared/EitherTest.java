package com.ligitabl.api.shared;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

class EitherTest {

    @Test
    void liftTry_preservesInterruptedFlag_onInterruptedException() {
        // Ensure clean state
        assertFalse(Thread.currentThread().isInterrupted(), "Precondition: thread not interrupted");

        Function<String, Either<Exception, Integer>> fn = Either.catching(s -> {
            throw new InterruptedException("boom");
        });

        Either<Exception, Integer> result = fn.apply("x");
        assertTrue(result.isLeft());
        assertTrue(Thread.currentThread().isInterrupted(), "Interrupted flag should be preserved");

        // Clear interrupted status for subsequent tests
        assertTrue(Thread.interrupted(), "Clearing interrupted flag should return true");
    }

    @Test
    void fromTry_preservesInterruptedFlag_onInterruptedException() {
        assertFalse(Thread.currentThread().isInterrupted(), "Precondition: thread not interrupted");

        Either<Exception, Integer> result = Either.catching(() -> {
            throw new InterruptedException("boom");
        });

        assertTrue(result.isLeft());
        assertTrue(Thread.currentThread().isInterrupted(), "Interrupted flag should be preserved");

        // Clear for subsequent tests
        assertTrue(Thread.interrupted());
    }

    @Test
    void catching_catchesException_butNotError() {
        Function<Integer, Either<String, Integer>> catcher = Either.catching(
                i -> {
                    throw new IllegalArgumentException("bad");
                },
                ex -> "E:" + ex.getClass().getSimpleName());

        Either<String, Integer> left = catcher.apply(1);
        assertTrue(left.isLeft());
        assertEquals("E:IllegalArgumentException", left.getLeft());

        Function<Integer, Either<String, Integer>> errorRethrow = Either.catching(
                i -> {
                    throw new AssertionError("error");
                },
                ex -> "ignored");

        assertThrows(AssertionError.class, () -> errorRethrow.apply(1));
    }

    @Test
    void catching_identity_left_isException() {
        Function<String, Either<Exception, Integer>> fn = Either.catching(s -> {
            throw new IllegalStateException("bad");
        });

        Either<Exception, Integer> res = fn.apply("x");
        assertTrue(res.isLeft());
        assertInstanceOf(IllegalStateException.class, res.getLeft());
    }

    @Test
    void recover_and_recoverWith_behave_as_expected() {
        Either<String, Integer> recovered = Either.<String, Integer>left("oops").recover(l -> 7);
        assertTrue(recovered.isRight());
        assertEquals(7, recovered.get());

        // recover should NOT invoke the mapper for Right
        java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean(false);
        Either<String, Integer> passthrough = Either.<String, Integer>right(5).recover(l -> {
            called.set(true);
            return 9;
        });
        assertTrue(passthrough.isRight());
        assertEquals(5, passthrough.get());
        assertFalse(called.get(), "recover mapper must not be called for Right");

        Either<String, Integer> recoveredWith =
                Either.<String, Integer>left("oops").recoverWith(l -> Either.right(9));
        assertTrue(recoveredWith.isRight());
        assertEquals(9, recoveredWith.get());

        // recoverWith should NOT invoke the mapper for Right
        called.set(false);
        Either<String, Integer> passthroughWith = Either.<String, Integer>right(11)
                .recoverWith(l -> {
                    called.set(true);
                    return Either.right(99);
                });
        assertTrue(passthroughWith.isRight());
        assertEquals(11, passthroughWith.get());
        assertFalse(called.get(), "recoverWith mapper must not be called for Right");

        // recoverWith can also return a Left to keep failure
        Either<String, Integer> stillLeft =
                Either.<String, Integer>left("oops").recoverWith(l -> Either.left("still-bad"));
        assertTrue(stillLeft.isLeft());
        assertEquals("still-bad", stillLeft.getLeft());
    }
}
