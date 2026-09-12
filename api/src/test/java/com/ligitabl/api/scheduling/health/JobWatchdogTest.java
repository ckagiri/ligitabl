package com.ligitabl.api.scheduling.health;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.notification.AdminNotificationService;

@ExtendWith(MockitoExtension.class)
class JobWatchdogTest {

    private static final String JOB = "testJob";
    private static final Duration INTERVAL = Duration.ofHours(1);

    @Mock
    private AdminNotificationService adminNotificationService;

    private MutableClock clock;
    private JobHeartbeat heartbeat;
    private JobWatchdog watchdog;

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock(Instant.parse("2026-09-12T10:00:00Z"));
        heartbeat = new JobHeartbeat(clock);
        watchdog = new JobWatchdog(heartbeat, adminNotificationService, clock);

        Field field = JobWatchdog.class.getDeclaredField("alertCooldownMinutes");
        field.setAccessible(true);
        field.set(watchdog, 360L);
    }

    @Test
    void noAlert_whenJobIsWithinItsInterval() {
        heartbeat.register(JOB, INTERVAL);
        heartbeat.ping(JOB);
        clock.advance(Duration.ofMinutes(30));

        watchdog.checkHeartbeats();

        verify(adminNotificationService, never()).notifyJobStalled(any(), any(), any(), any(), anyLong());
    }

    @Test
    void alerts_whenJobIsStale() {
        heartbeat.register(JOB, INTERVAL);
        heartbeat.ping(JOB);
        clock.advance(Duration.ofHours(3));

        watchdog.checkHeartbeats();

        verify(adminNotificationService).notifyJobStalled(eq(JOB), any(), any(), eq(INTERVAL), eq(1L));
    }

    @Test
    void doesNotRepeatAlert_withinCooldown() {
        heartbeat.register(JOB, INTERVAL);
        clock.advance(Duration.ofHours(3));

        watchdog.checkHeartbeats();
        clock.advance(Duration.ofHours(1));
        watchdog.checkHeartbeats();

        verify(adminNotificationService, times(1)).notifyJobStalled(any(), any(), any(), any(), anyLong());
    }

    @Test
    void alertsAgain_afterCooldownElapses() {
        heartbeat.register(JOB, INTERVAL);
        clock.advance(Duration.ofHours(3));

        watchdog.checkHeartbeats();
        clock.advance(Duration.ofHours(7)); // past the 6h cooldown
        watchdog.checkHeartbeats();

        verify(adminNotificationService, times(2)).notifyJobStalled(any(), any(), any(), any(), anyLong());
    }

    /** A job that recovers then breaks again must alert promptly, not wait out the old cooldown. */
    @Test
    void alertsAgainPromptly_afterRecoveryThenStall() {
        heartbeat.register(JOB, INTERVAL);
        clock.advance(Duration.ofHours(3));
        watchdog.checkHeartbeats();

        heartbeat.ping(JOB);
        clock.advance(Duration.ofHours(3));
        watchdog.checkHeartbeats();

        verify(adminNotificationService, times(2)).notifyJobStalled(any(), any(), any(), any(), anyLong());
    }

    @Test
    void noAlert_whenNothingRegistered() {
        watchdog.checkHeartbeats();

        verify(adminNotificationService, never()).notifyJobStalled(any(), any(), any(), any(), anyLong());
    }

    /** The watchdog must never become the thing that breaks. */
    @Test
    void swallowsNotificationFailure() {
        heartbeat.register(JOB, INTERVAL);
        clock.advance(Duration.ofHours(3));
        doThrow(new RuntimeException("slack down"))
                .when(adminNotificationService)
                .notifyJobStalled(any(), any(), any(), any(), anyLong());

        watchdog.checkHeartbeats();

        verify(adminNotificationService).notifyJobStalled(any(), any(), any(), any(), anyLong());
    }
}
