package com.ligitabl.api.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * A frozen clock for tests whose fixtures are dated relative to real time.
 *
 * <p>⚠️ <b>Test support only.</b> A static "now" in production code would be exactly the global
 * clock read that {@code Season}'s predicates were rewritten to remove — production code takes an
 * injected {@link Clock} and passes {@code clock.instant()} explicitly. This exists so tests can
 * stop repeating the same three-argument incantation, not so they can skip injection.
 *
 * <p><b>Why anchored to {@link Instant#now()} rather than a fixed date.</b> The classes using this
 * build their season/round fixtures relative to real time. Pinning the clock to a historical
 * instant without also re-dating those fixtures makes the two describe different worlds — a
 * fixture that says "the season starts tomorrow" against a clock that says 2024 is not a test of
 * anything. That mismatch is not hypothetical: it is what turned the {@code Season} clock
 * migration from a mechanical change into one failing and twelve erroring tests.
 *
 * <p>So freezing here buys one specific thing — the instant cannot move <em>during</em> a test,
 * which is what made assertions near a date boundary unreliable — and deliberately not the other
 * — determinism across runs, which requires converting the fixtures too. Do that per class, where
 * each conversion is verifiable on its own; a class that needs a specific calendar position should
 * declare its own constant instead of using this.
 */
public final class TestClock {

    /** The instant {@link #FIXED} is frozen at. Prefer this to {@code clock.instant()} in tests. */
    public static final Instant NOW = Instant.now();

    /** Frozen at {@link #NOW}, UTC — matching the application's {@code Clock.systemUTC()} bean. */
    public static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    private TestClock() {}
}
