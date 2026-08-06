package com.ligitabl.api.testsupport;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application's {@code Clock} bean with a frozen one, for DB-backed integration tests.
 *
 * <p>Add {@code @Import(FixedClockConfig.class)} to the test class and inject {@code Clock} as
 * normal; assert against {@link #NOW} rather than reading the clock back.
 *
 * <p><b>Prefer this to a mocked {@code Clock} bean.</b> Every mock it replaced returned one instant
 * unconditionally and none verified interactions, so the mock was ceremony — and a mocked
 * {@code Clock} silently hands a {@code null} instant to any code path that starts consulting it,
 * which is precisely how it bit when {@code Season}'s phase predicates began taking an explicit
 * instant.
 *
 * <p>⚠️ <b>Shared deliberately, and being one class is the point.</b> Spring caches a test context
 * per distinct configuration and never closes it, so connections against the single Testcontainers
 * Postgres scale with the number of distinct *configurations*, not with tests running. Seven copies
 * of this as a nested class in seven test classes meant seven contexts, and the suite reached
 * {@code FATAL: sorry, too many clients already}. One shared class is one context key.
 * ({@link AbstractPostgresIT} also caps the pool size, so neither alone is load-bearing — but a
 * private copy of this in a new test class puts the count back up.)
 *
 * <p>If a test genuinely needs a different instant, it needs its own configuration and its own
 * context — which is a real cost, so make sure the instant is actually load-bearing first. Most are
 * not: they either never reference it, or use it relatively.
 */
@TestConfiguration
public class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(TestCalendar.MID_SEASON, ZoneOffset.UTC);
    }
}
