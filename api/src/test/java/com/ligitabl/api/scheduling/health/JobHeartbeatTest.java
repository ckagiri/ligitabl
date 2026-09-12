package com.ligitabl.api.scheduling.health;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobHeartbeatTest {

    private static final String JOB = "testJob";
    private static final Duration INTERVAL = Duration.ofHours(1);

    private MutableClock clock;
    private JobHeartbeat heartbeat;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-12T10:00:00Z"));
        heartbeat = new JobHeartbeat(clock);
    }

    private JobHeartbeat.Entry entry() {
        return heartbeat.snapshot().stream()
                .filter(e -> e.jobName().equals(JOB))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void freshRegistrationIsNotStale() {
        heartbeat.register(JOB, INTERVAL);

        assertFalse(heartbeat.isStale(entry(), clock.instant()));
        assertEquals(0L, entry().pingCount());
        assertNull(entry().lastPingAt());
    }

    /** The JOIN_REMINDER-style case: the bean exists but its schedule never fires. */
    @Test
    void neverPingedPastDeadlineIsStale() {
        heartbeat.register(JOB, INTERVAL);

        // Deadline is 1h * 1.5 = 90 min from registration.
        clock.advance(Duration.ofMinutes(85));
        assertFalse(heartbeat.isStale(entry(), clock.instant()));

        clock.advance(Duration.ofMinutes(10));
        assertTrue(heartbeat.isStale(entry(), clock.instant()));
    }

    @Test
    void pingClearsStaleness() {
        heartbeat.register(JOB, INTERVAL);
        clock.advance(Duration.ofHours(3));
        assertTrue(heartbeat.isStale(entry(), clock.instant()));

        heartbeat.ping(JOB);

        assertFalse(heartbeat.isStale(entry(), clock.instant()));
        assertEquals(1L, entry().pingCount());
    }

    @Test
    void pingClearsTheAlertCooldown() {
        heartbeat.register(JOB, INTERVAL);
        heartbeat.markAlerted(JOB, clock.instant());
        assertNotNull(entry().lastAlertAt());

        heartbeat.ping(JOB);

        assertNull(entry().lastAlertAt(), "a recovered job must be able to alert again promptly");
    }

    @Test
    void graceIsFlooredForFastJobs() {
        // 1.5x of 10s is 15s — the 5-minute floor has to govern instead.
        Duration grace = JobHeartbeat.graceFor(Duration.ofSeconds(10));

        assertTrue(grace.compareTo(Duration.ofMinutes(5)) >= 0, "expected >=5m floor, got " + grace);
    }

    @Test
    void graceScalesForSlowJobs() {
        assertEquals(Duration.ofHours(36), JobHeartbeat.graceFor(Duration.ofHours(24)));
    }

    @Test
    void reRegisteringKeepsPingHistory() {
        heartbeat.register(JOB, INTERVAL);
        heartbeat.ping(JOB);

        heartbeat.register(JOB, Duration.ofHours(2));

        assertEquals(1L, entry().pingCount());
        assertEquals(Duration.ofHours(2), entry().maxInterval());
    }

    /** A typo'd job name must not throw and break the job that used it. */
    @Test
    void pingingAnUnregisteredJobTracksItAnyway() {
        heartbeat.ping("neverRegistered");

        assertEquals(1, heartbeat.snapshot().size());
        assertEquals("neverRegistered", heartbeat.snapshot().get(0).jobName());
    }

    /** Covers the compute()-not-put() decision in ping(). */
    @Test
    void concurrentPingsAreAllCounted() throws Exception {
        heartbeat.register(JOB, INTERVAL);
        int threads = 16;
        int perThread = 50;

        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        heartbeat.ping(JOB);
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "pings did not finish");
        } finally {
            pool.shutdownNow();
        }

        assertEquals((long) threads * perThread, entry().pingCount());
    }
}
