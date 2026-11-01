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
 * normal; assert against {@link #NOW} rather than reading the clock back. Prefer it to a mocked
 * {@code Clock}, which hands {@code null} to any code path that starts consulting it.
 *
 * <p>⚠️ Keep it one shared class. Spring caches a context per distinct configuration, and each
 * opens connections against the one Postgres container, so a private copy in a test class adds a
 * context and counts toward {@code FATAL: sorry, too many clients already}. A test that needs a
 * different instant pays for its own context; most use the instant relatively or not at all.
 */
@TestConfiguration
public class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(TestCalendar.MID_SEASON, ZoneOffset.UTC);
    }
}
