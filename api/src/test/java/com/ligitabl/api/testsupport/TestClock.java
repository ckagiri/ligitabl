package com.ligitabl.api.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * A frozen clock for tests whose fixtures are dated relative to real time.
 *
 * <p>⚠️ <b>Test support only.</b> Production code takes an injected {@link Clock} and passes
 * {@code clock.instant()}; a static "now" there is the global clock read the injection removes.
 *
 * <p>Anchored to {@link Instant#now()} because these classes' fixtures are relative to real time,
 * and a fixed clock without re-dated fixtures describes another world. It stops the instant moving
 * during a test, not drift across runs; a class needing a specific calendar position should declare
 * its own constant.
 */
public final class TestClock {

    /** The instant {@link #FIXED} is frozen at. Prefer this to {@code clock.instant()} in tests. */
    public static final Instant NOW = Instant.now();

    /** Frozen at {@link #NOW}, UTC — matching the application's {@code Clock.systemUTC()} bean. */
    public static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    /**
     * {@link #NOW} as a calendar date, <b>in UTC</b>. Use this in season fixtures instead of
     * {@code LocalDate.now()}.
     *
     * <p>⚠️ They differ: {@code LocalDate.now()} uses the JVM zone, while
     * {@code Season.getSeasonState(Instant)} uses UTC. East of UTC they disagree late each UTC day, so
     * "{@code endDate} was yesterday" reads as today and OFF_SEASON becomes INACTIVE for a few hours.
     */
    public static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneOffset.UTC);

    private TestClock() {}
}
